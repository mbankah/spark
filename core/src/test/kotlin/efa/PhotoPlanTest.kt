package efa

import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.*

/** PH1: when to take which photo. */
class PhotoPlanTest {
    private val mon = LocalDate.of(2026, 11, 2)
    private val slots = WeekB.slots
    private fun day(d: LocalDate = mon) = slotsFor(slots, d)
    private val lecture get() = day().first { it.slot.type == "lecture" }
    private val study get() = day().first { it.slot.type == "study-standard" || it.slot.type == "study" || it.slot.type == "study-light" }
    private fun plan(now: java.time.ZonedDateTime, verified: Set<String> = emptySet(), night: Long? = null) = photoPlan(slots, now, verified, night)
    private fun of(p: List<PhotoDue>, s: DatedSlot) = p.single { it.key == blockKey(s) }

    @Test fun `PH1 a lecture photo is upcoming before it starts, due inside the lecture and missed after it`() {
        val l = lecture
        assertEquals(PhotoState.UPCOMING, of(plan(l.start.minusMinutes(30)), l).state)
        assertEquals(PhotoState.DUE, of(plan(l.start.plusMinutes(10)), l).state)
        assertEquals(PhotoState.MISSED, of(plan(l.end.plusMinutes(1)), l).state)
        val p = of(plan(l.start.plusMinutes(10)), l)
        assertEquals(PhotoKind.ATTENDANCE, p.kind); assertEquals(l.start, p.opens); assertEquals(l.end, p.dueBy)
    }

    @Test fun `PH1 a study block photo opens when the block ends, counts in full for 15 minutes, then still counts as recovered time`() {
        val s = study
        assertEquals(PhotoState.UPCOMING, of(plan(s.end.minusMinutes(5)), s).state)
        assertEquals(PhotoState.DUE, of(plan(s.end.plusMinutes(1)), s).state)
        assertEquals(PhotoState.DUE, of(plan(s.end.plusMinutes(15)), s).state)
        assertEquals(PhotoState.LATE, of(plan(s.end.plusMinutes(16)), s).state)
        val wake = nextWake(slots, s.end)!!
        assertEquals(PhotoState.LATE, of(plan(wake.minusMinutes(1)), s).state)
        assertEquals(s.end.plusMinutes(15), of(plan(s.end.plusMinutes(1)), s).dueBy)
    }

    @Test fun `PH1 a block that is already proved is done whatever the time`() {
        val s = study; val l = lecture
        val done = setOf(blockKey(s), blockKey(l))
        assertEquals(PhotoState.DONE, of(plan(s.end.plusMinutes(5), done), s).state)
        assertEquals(PhotoState.DONE, of(plan(l.start.plusMinutes(5), done), l).state)
        assertEquals(PhotoState.DONE, of(plan(s.end.plusHours(30), done), s).state)
    }

    @Test fun `PH1 the window agrees with what the verifier accepts`() {
        val s = study; val l = lecture
        val monday = s.end.toLocalDate()
        val zone = s.end.zone
        for (m in listOf(-1L, 0L, 14L, 15L, 16L, 40L)) {
            val now = s.end.plusMinutes(m)
            val verdict = verifyProof(Proof.WORK_PHOTO, s.start, s.end, now, true, false, null, nextWake(slots, s.end)!!, 0)
            val state = of(plan(now), s).state
            assertEquals(verdict.verified && !verdict.recovered, state == PhotoState.DUE, "work +$m: $verdict $state")
            if (m >= 0) assertEquals(verdict.verified, state == PhotoState.DUE || state == PhotoState.LATE, "work +$m")
        }
        for (m in listOf(-10L, 0L, 30L, 59L, 61L)) {
            val now = l.start.plusMinutes(m)
            val verdict = verifyProof(Proof.ATTENDANCE, l.start, l.end, now, true, false, LocationResult.NEAR_VENUE, nextWake(slots, l.end)!!, 0)
            assertEquals(verdict.verified, of(plan(now), l).state == PhotoState.DUE, "lecture $m: $verdict")
        }
        assertNotNull(monday); assertNotNull(zone)
    }

    @Test fun `PH1 a code block asks for a commit, not a photo, and the words say so`() {
        val code = listOf(PatternSlot(mon.dayOfWeek, 20 * 60, 21 * 60, "CS Project", "code", "", false, ""))
        val p = photoPlan(code + slots.filter { it.title == "Wake, shower" }, at(mon, LocalTime.of(21, 5)), emptySet()).single { it.kind == PhotoKind.COMMIT }
        assertEquals("Take the commit now", p.headline()); assertTrue(p.detail().contains("Push your commit"))
    }

    @Test fun `PH1 the words name the time and the place`() {
        val l = lecture
        val p = of(plan(l.start.plusMinutes(5)), l)
        assertEquals("Take the photo now", p.headline())
        assertTrue(p.detail().contains(l.slot.title.substringBefore(" - ")) && p.detail().contains(l.slot.venue) && p.detail().contains("before 10:00"), p.detail())
        assertEquals("Photo at 09:00", of(plan(l.start.minusMinutes(30)), l).headline())
        val s = study
        assertTrue(of(plan(s.end.plusMinutes(3)), s).detail().contains("before ${java.time.format.DateTimeFormatter.ofPattern("HH:mm").format(s.end.plusMinutes(15))} to count in full"))
    }

    @Test fun `PH1 the night photo opens two hours before the cutoff and is due by it`() {
        val tue = mon.plusDays(1)
        val wake = slotsFor(slots, tue).first { it.slot.title == "Wake, shower" }.start
        val cutoff = nightCutoff(wake)
        val before = plan(cutoff.minusHours(3)).first { it.kind == PhotoKind.NIGHT && it.dueBy == cutoff }
        assertEquals(PhotoState.UPCOMING, before.state)
        assertEquals(PhotoState.DUE, plan(cutoff.minusMinutes(30)).first { it.kind == PhotoKind.NIGHT && it.dueBy == cutoff }.state)
        assertEquals(PhotoState.LATE, plan(cutoff.plusMinutes(10)).first { it.kind == PhotoKind.NIGHT && it.dueBy == cutoff }.state)
        val taken = cutoff.minusHours(1).toInstant().toEpochMilli()
        assertEquals(PhotoState.DONE, plan(cutoff.minusMinutes(30), night = taken).first { it.kind == PhotoKind.NIGHT && it.dueBy == cutoff }.state)
        val old = cutoff.minusHours(10).toInstant().toEpochMilli()
        assertEquals(PhotoState.DUE, plan(cutoff.minusMinutes(30), night = old).first { it.kind == PhotoKind.NIGHT && it.dueBy == cutoff }.state, "last night's photo does not count for tonight")
    }

    @Test fun `PH1 the focus is the open photo that runs out first, else the next to open`() {
        val l = lecture
        val p = plan(l.start.plusMinutes(5))
        assertEquals(blockKey(l), photoFocus(p)?.key)
        val early = plan(l.start.minusMinutes(20))
        assertEquals(PhotoState.UPCOMING, photoFocus(early)?.state); assertEquals(blockKey(l), photoFocus(early)?.key)
        val dawn = plan(l.start.minusHours(2))          // last night's proofs can still be given late, so one of those is shown, not the lecture
        assertEquals(PhotoState.LATE, photoFocus(dawn)?.state); assertNotEquals(blockKey(l), photoFocus(dawn)?.key)
        assertNull(photoFocus(emptyList()))
    }

    @Test fun `PH1 a reminder sounds when a photo opens and five minutes before it stops counting in full`() {
        val l = lecture
        val rs = photoReminders(plan(l.start.minusMinutes(30))).filter { it.key == blockKey(l) }
        assertEquals(listOf(l.start, l.end.minusMinutes(5)), rs.map { it.at })
        assertEquals(listOf(false, true), rs.map { it.lastCall })
        assertEquals("Photo due", rs[0].title); assertEquals("5 minutes left", rs[1].title)
        val s = study
        val ws = photoReminders(plan(s.end.minusMinutes(30))).filter { it.key == blockKey(s) }
        assertEquals(listOf(s.end, s.end.plusMinutes(10)), ws.map { it.at })
    }

    @Test fun `PH1 a done or missed photo gives no reminder, and the next one is strictly after now`() {
        val l = lecture
        assertTrue(photoReminders(plan(l.start.minusMinutes(30), setOf(blockKey(l)))).none { it.key == blockKey(l) })
        assertTrue(photoReminders(plan(l.end.plusMinutes(5))).none { it.key == blockKey(l) })
        val p = plan(l.start.minusMinutes(30))
        assertEquals(l.start, nextPhotoReminder(p, l.start.minusMinutes(1))?.at.takeIf { nextPhotoReminder(p, l.start.minusMinutes(1))?.key == blockKey(l) })
        assertTrue(nextPhotoReminder(p, l.start)!!.at.isAfter(l.start))
    }

    @Test fun `PH1 a day off has no photos for the work and lectures it took away`() {
        val off = slots + dayOffMarker(mon)
        val p = photoPlan(off, at(mon, LocalTime.of(9, 30)), emptySet())
        assertTrue(p.none { it.slot?.plannedDay == mon && (it.kind == PhotoKind.ATTENDANCE || it.kind == PhotoKind.WORK) })
    }

    @Test fun `PH1 blocks that need no proof never appear`() {
        val p = plan(at(mon, LocalTime.of(12, 0)))
        assertTrue(p.mapNotNull { it.slot }.none { proofFor(it.slot) == Proof.NONE })
    }
}

class PhotoScheduleTagTest {
    private val mon = LocalDate.of(2026, 11, 2)
    private val slots = WeekB.slots

    @Test fun `PH1 the schedule says on each block that needs one what photo to take and when`() {
        val now = at(mon, LocalTime.of(8, 0))
        val day = scheduleView(slots, emptyList(), null, now, days = 1).single()
        val lecture = day.rows.first { it.type == "lecture" }
        assertEquals(PhotoKind.ATTENDANCE, lecture.photo?.kind); assertEquals("Photo ${lecture.start} to ${lecture.end}", lecture.photo?.tag())
        assertTrue(day.rows.filter { it.photo != null }.all { proofFor(PatternSlot(mon.dayOfWeek, 0, 1, "", it.type, "", false, "")) != Proof.NONE })
        assertTrue(day.rows.filter { it.photo == null }.all { it.type !in setOf("lecture", "study-standard", "study-light", "study", "code") })
    }

    @Test fun `PH1 a proved block says done and a passed one says missed`() {
        val l = slotsFor(slots, mon).first { it.slot.type == "lecture" }
        val after = at(mon, LocalTime.of(13, 0))
        val missed = scheduleView(slots, emptyList(), null, after, days = 1).single().rows.first { it.type == "lecture" }
        assertEquals("Photo missed", missed.photo?.tag())
        val done = scheduleView(slots, emptyList(), null, after, days = 1, verified = setOf(blockKey(l))).single().rows.first { it.type == "lecture" }
        assertEquals("Photo done", done.photo?.tag())
        val running = scheduleView(slots, emptyList(), null, l.start.plusMinutes(5), days = 1).single().rows.first { it.type == "lecture" }
        assertEquals("Take it now", running.photo?.tag())
    }
}
