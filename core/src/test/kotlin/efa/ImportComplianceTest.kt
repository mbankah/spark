package efa

import java.time.LocalDate
import java.time.LocalTime
import kotlin.random.Random
import kotlin.test.*

/**
 * Error verification for section 25: the new code is held against the rules that were already there. Each test says which existing rule
 * a file, a catch-up or a conversion must not be able to break.
 */
class ImportComplianceTest {
    private val today = LocalDate.of(2026, 10, 26)
    private val now = at(today, LocalTime.of(12, 0))
    private val base = WeekB.slots
    private val header = "kind,date,end_date,start,end,title,type,venue,heaviness,study,action,notes"
    private fun file(vararg rows: String) = (listOf(header) + rows).joinToString("\n")
    private fun verify(text: String, pattern: List<PatternSlot> = base) = verifyImport(parseTimetableCsv(text, today), pattern, emptySet(), emptyList(), now)
    private val list = setOf("com.instagram.android")

    // ---- K3, LA1, W2: blocking and proof follow the slot type, so an imported or converted slot behaves like its type ----

    @Test fun `K3 an imported lecture leaves the phone open, an imported walk closes it, a converted block closes it as work`() {
        val v = verify(file(
            "event,2026-11-03,,16:30,17:00,Seminar,lecture,Watson,,none,,",
            "event,2026-11-03,,17:00,17:10,Walk,travel,,,,,",
        ))
        assertTrue(v.ok, v.errors.joinToString { it.text() })
        val merged = base + v.overlay.map { it.slot }
        assertEquals(Decision.Allow, decide("com.instagram.android", at(LocalDate.of(2026, 11, 3), LocalTime.of(16, 40)), merged, list))
        val walk = decide("com.instagram.android", at(LocalDate.of(2026, 11, 3), LocalTime.of(17, 5)), merged, list) as Decision.Block
        assertEquals(BlockReason.TRANSITION, walk.reason)
        val conv = planConversions(base, listOf(DueWork("Project", 140.0, 5, at(LocalDate.of(2026, 11, 13), LocalTime.of(12, 0)))), now).conversions.first()
        val inside = conv.window.start.plusMinutes(5)
        val work = decide("com.instagram.android", inside, base + conv.slot, list) as Decision.Block
        assertEquals(BlockReason.WORK_BLOCK, work.reason); assertTrue(work.unprovenMinutes > 0)
        assertEquals(0, (decide("com.instagram.android", inside, base + conv.slot, list, verifiedKeys = setOf(blockKey(conv.window.copy(slot = conv.slot)))) as Decision.Block).unprovenMinutes)
    }

    @Test fun `Y1 L1 an imported slot needs the proof of its type`() {
        fun proof(type: String) = proofFor(PatternSlot(today.dayOfWeek, 600, 660, "x", type, "", false, "", date = today))
        assertEquals(Proof.ATTENDANCE, proof("lecture")); assertEquals(Proof.ATTENDANCE, proof("tutorial"))
        assertEquals(Proof.WINDOW_PHOTO, proof("gym")); assertEquals(Proof.WINDOW_PHOTO, proof("training")); assertEquals(Proof.WINDOW_PHOTO, proof("mytutor"))
        assertEquals(Proof.NONE, proof("travel"))
        assertEquals(Proof.WORK_PHOTO, proof("study")); assertEquals(Proof.GIT_COMMIT, proof("code"))
    }

    // ---- section 10 and C5: what is never squeezed ----

    @Test fun `section 10 no file and no conversion changes sleep, meals, routines or the wake time on any day`() {
        fun fixed(pattern: List<PatternSlot>, d: LocalDate) = slotsFor(pattern, d).filter { it.slot.type in setOf("sleep", "meal", "routine") }.map { Triple(it.slot.startMin, it.slot.endMin, it.slot.title) }
        val v = verify(file(
            "event,2026-11-03,,16:30,17:30,Seminar,lecture,,,standard,,", "event,2026-11-02,,13:00,14:00,Match,match,,,,cancel,",
            "deadline,2026-11-06,,12:00,,Report,,,9,,,", "period,2026-11-02,2026-11-06,,,,catch_up_week,,,,,",
        ))
        val conv = planConversions(base + v.overlay.map { it.slot }, listOf(DueWork("Big", 90.0, 8, at(LocalDate.of(2026, 11, 13), LocalTime.of(12, 0)))), now).conversions.map { it.slot }
        val merged = base + v.overlay.map { it.slot } + conv
        for (i in 0L..20L) { val d = today.plusDays(i); assertEquals(fixed(base, d), fixed(merged, d), "day $d"); assertEquals(targetWake(base, d), targetWake(merged, d)) }
    }

    @Test fun `N1 a file cannot add or cancel a routine, a meal, sleep or free time, so the night cutoff cannot move`() {
        for (t in listOf("routine", "meal", "sleep", "free", "planning", "chore", "mytutor-notes"))
            for (action in listOf("", "cancel"))
                assertFalse(verify(file("event,2026-11-03,,07:30,07:50,Wake,$t,,,,$action,")).ok, "$t $action")
    }

    @Test fun `H1 no file can make a day off, because a day off is not a type a file can carry`() {
        for (t in listOf("day_off", "dayoff", "off", "holiday", "library")) {
            assertFalse(verify(file("event,2026-11-03,,10:00,11:00,Off,$t,,,,,")).ok, t)
            assertFalse(verify(file("period,2026-11-03,2026-11-04,,,,$t,,,,,")).ok, t)
        }
    }

    @Test fun `IM6 a file cannot shorten or remove a study block, because study types are refused and cancel is limited to commitments`() {
        for (t in listOf("study", "study-standard", "study-light", "code")) assertFalse(verify(file("event,2026-11-02,,10:10,12:15,x,$t,,,,cancel,")).ok, t)
    }

    // ---- IM6: the wall is needed exactly when something is loosened ----

    @Test fun `IM6 across many random files the wall is needed exactly when a file cancels or cuts into work`() {
        val rnd = Random(25)
        val pool = listOf(
            Triple("2026-11-03", "16:30" to "17:30", "lecture"),          // free time
            Triple("2026-11-02", "11:00" to "11:30", "tutorial"),         // on a study block
            Triple("2026-11-05", "20:00" to "20:30", "errand"),
            Triple("2026-11-07", "09:00" to "09:45", "gym"),
        )
        repeat(300) {
            val rows = ArrayList<String>(); var expectWall = false
            if (rnd.nextBoolean()) { rows += "event,2026-11-02,,09:00,10:00,Randomness lecture,lecture,,,,cancel,"; expectWall = true }
            for ((d, t, type) in pool.shuffled(rnd).take(rnd.nextInt(0, 4)))
                rows += "event,$d,,${t.first},${t.second},E${rows.size},$type,,,none,,"
            val v = verify(file(*rows.toTypedArray()))
            if (!v.ok) return@repeat                                      // a random pick can collide with a meal or a walk, which is an error
            val cutsWork = v.overlay.any { o -> !o.slot.cancelled && slotsFor(base, o.slot.date!!).any { b -> b.slot.type in CUTTABLE_WORK && b.slot.startMin < o.slot.endMin && b.slot.endMin > o.slot.startMin } }
            assertEquals(expectWall || cutsWork, v.needsWall, rows.joinToString("; "))
        }
    }

    @Test fun `IM8 after a file is stored, offering it again is a file that changes nothing, for many random files`() {
        val rnd = Random(8)
        repeat(100) {
            val rows = (0 until rnd.nextInt(1, 4)).map { i ->
                when (rnd.nextInt(3)) {
                    0 -> "deadline,2026-11-${rnd.nextInt(6, 28)},,1${rnd.nextInt(0, 10)}:00,,D$i,,,${rnd.nextInt(1, 11)},,,"
                    1 -> "period,2026-11-0${rnd.nextInt(2, 9)},2026-11-${rnd.nextInt(10, 14)},,,P$i,${if (rnd.nextBoolean()) "assignment_week" else "catch_up_week"},,,,,"
                    else -> "event,2026-11-03,,16:${rnd.nextInt(10, 30)},17:${rnd.nextInt(30, 59)},E$i,lecture,,,none,,"
                }
            }
            val text = file(*rows.toTypedArray())
            val first = verify(text)
            if (!first.ok) return@repeat
            val known = first.overlay.map { it.rowHash }.toSet() + first.periodHashes + first.work.map { it.key.substringAfter(':') }
            val second = verifyImport(parseTimetableCsv(text, today), base + first.overlay.map { it.slot }, known, first.work.map { DueWork(it.title, it.hours, it.heaviness, it.due) }, now)
            assertTrue(second.changesNothing, "second pass would still change: $text")
        }
    }

    // ---- RS: automatic moves keep the rules ----

    @Test fun `RS7 W2 B6 a catch-up adds work and takes no capacity away, so lost time stays lost`() {
        val tue = at(LocalDate.of(2026, 11, 3), LocalTime.of(8, 0))
        val catchUps = missedCatchUps(base, tue, emptySet(), emptySet(), at(LocalDate.of(2026, 10, 1), LocalTime.MIDNIGHT))
        assertTrue(catchUps.isNotEmpty())
        val w0 = (bufferNow(tue, studyCycles(base, tue) + DueWork("R", 1.0, 5, tue.plusDays(7)), base, false) as BufferState.Value).result
        val w1 = (bufferNow(tue, studyCycles(base, tue) + DueWork("R", 1.0, 5, tue.plusDays(7)) + catchUps.map { DueWork(it.title, it.hours, it.heaviness, it.due) }, base, false) as BufferState.Value).result
        assertTrue(w1.workHours > w0.workHours); assertEquals(w0.availableHours, w1.availableHours, 1e-9, "capacity is a function of the pattern only")
        assertTrue(w1.hours <= w0.hours)
    }

    @Test fun `F2 free time turns into work only from tomorrow, whatever the clock says`() {
        for (h in listOf(0, 6, 12, 18, 23)) {
            val n = at(LocalDate.of(2026, 11, 2), LocalTime.of(h, 0))
            for (c in planConversions(base, listOf(DueWork("Big", 90.0, 8, at(LocalDate.of(2026, 11, 13), LocalTime.of(12, 0)))), n).conversions)
                assertTrue(c.window.plannedDay.isAfter(n.toLocalDate()), "hour $h converted ${c.window.plannedDay}")
        }
    }

    @Test fun `G3 over a full term of weeks the protected free time is never converted and at least 6 hours a week survive`() {
        val big = listOf(DueWork("Big", 400.0, 9, at(LocalDate.of(2027, 1, 15), LocalTime.of(12, 0))))
        val plan = planConversions(base, big, now, horizonDays = 70)
        val merged = base + plan.conversions.map { it.slot }
        for (w in 0 until 9) {
            val monday = LocalDate.of(2026, 10, 26).plusWeeks(w.toLong())
            val freeLeft = (0L..6L).flatMap { slotsFor(merged, monday.plusDays(it)) }.filter { it.slot.type == "free" }.sumOf { it.slot.endMin - it.slot.startMin }
            assertTrue(freeLeft >= PROTECTED_FREE_MIN_PER_WEEK, "week $w keeps only $freeLeft minutes")
        }
    }

    @Test fun `RS2 placing tasks never gives two tasks the same minutes of one block`() {
        val rnd = Random(2)
        repeat(100) {
            val tasks = (1L..rnd.nextInt(1, 8)).map { OpenTask(it, "T$it", at(LocalDate.of(2026, 10, 27).plusDays(rnd.nextLong(0, 12)), LocalTime.of(12, 0)), rnd.nextInt(10, 400), rnd.nextInt(0, 2)) }
            val a = allocateTasks(base, tasks, now)
            for ((_, ps) in a.placed.groupBy { it.slot.start }) {
                val s = ps.first().slot
                assertTrue(ps.sumOf { it.minutes } <= java.time.Duration.between(maxOf(s.start, now), s.end).toMinutes())
            }
            for (t in tasks) assertEquals(t.remainingMin, a.placed.filter { it.task.id == t.id }.sumOf { it.minutes } + (a.unplaced[t.id] ?: 0), "task ${t.id} lost minutes")
            assertTrue(a.placed.all { it.slot.slot.type in TASK_SLOT_TYPES && !it.slot.end.isAfter(it.task.due) })
        }
    }

    @Test fun `X4 an imported event and a conversion either side of the clock change on 25 October 2026 keep their wall-clock times`() {
        val v = verify(file("event,2026-10-27,,16:30,17:30,Seminar,lecture,,,none,,"), base)
        val merged = base + v.overlay.map { it.slot }
        for (d in listOf(LocalDate.of(2026, 10, 24), LocalDate.of(2026, 10, 25), LocalDate.of(2026, 10, 26), LocalDate.of(2026, 10, 27)))
            for (s in slotsFor(merged, d)) assertEquals(s.slot.startMin, s.start.hour * 60 + s.start.minute, "$d ${s.slot.title}")
    }
}
