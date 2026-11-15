package app.efa

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.efa.data.*
import efa.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime
import kotlin.random.Random
import kotlin.test.*

/**
 * Random sequences of applying and removing modifiers and planning ordinary days off, on a real Room database. Whatever the order: the tally never goes down, nothing
 * a modifier made is left behind once it is removed, an ordinary day off is never taken by removing a modifier, and with every modifier removed the calendar is the original.
 */
@RunWith(AndroidJUnit4::class)
class ModifyLifecycleFuzzTest {
    private val now = at(LocalDate.of(2026, 11, 2), LocalTime.of(9, 0))

    private fun randomInstance(r: Random, preset: Preset): Instance {
        val today = now.toLocalDate(); val start = today.plusDays(r.nextLong(1, 12))
        return when (preset) {
            Preset.ASSIGNMENT -> Instance(preset, today, today.plusDays(40), emptyMap(), listOf(mapOf("title" to "A${r.nextInt(3)}", "due" to today.plusDays(r.nextLong(5, 25)).toString(), "due_time" to "17:00", "size_h" to (2 + r.nextInt(10)).toString())))
            Preset.FOCUS -> Instance(preset, start, start.plusDays(r.nextLong(3, 8)), mapOf("hybrid" to r.nextBoolean().toString()), listOf(mapOf("title" to "P", "due" to start.plusDays(12).toString(), "due_time" to "17:00", "size_h" to "12.0")))
            Preset.PERIOD_OFF -> Instance(preset, start, start.plusDays(r.nextLong(0, 9)), mapOf("lectures" to listOf("auto", "running", "none").random(r)))
            else -> Instance(preset, start, start.plusDays(r.nextLong(0, 6)))
        }
    }

    private fun shape(slots: List<PatternSlot>): List<String> =
        generateSequence(now.toLocalDate()) { it.plusDays(1) }.take(45).flatMap { d -> slotsFor(slots, d).map { "$d ${it.slot.startMin}-${it.slot.endMin} ${it.slot.title} ${it.slot.type}" } }.toList()

    @Test fun random_apply_remove_and_day_off_sequences_leave_nothing_behind() = runBlocking<Unit> {
        val failures = ArrayList<String>(); var applied = 0; var refused = 0; var removed = 0
        for (seed in 1..25) {
            val r = Random(seed)
            val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), EfaDb::class.java).build()
            try {
                db.seedIfEmpty(at(LocalDate.of(2026, 10, 21), LocalTime.of(9, 0)).toInstant().toEpochMilli(), LONDON.id)
                val plan = PlanRepo(db); val repo = ModifierRepo(db)
                val original = shape(plan.slots())
                val planned = ArrayList<LocalDate>()
                var tally = usesInLast30Days(plan.uses(db), now)
                val ids = ArrayList<Long>()
                for (step in 0 until 8) {
                    when (r.nextInt(4)) {
                        0, 1 -> {
                            val inst = randomInstance(r, listOf(Preset.ASSIGNMENT, Preset.FOCUS, Preset.PERIOD_OFF, Preset.CATCH_UP, Preset.LECTURE_FREE).random(r))
                            if (repo.preview(inst, now).canApply) { ids += repo.apply(inst, now, 100); applied++ } else refused++
                        }
                        2 -> if (ids.isNotEmpty()) { repo.remove(ids.removeAt(r.nextInt(ids.size))); removed++ }
                        else -> {
                            val d = now.toLocalDate().plusDays(r.nextLong(14, 28))
                            if (db.daysOff().get(d.toString()) == null) { plan.addDaysOff(db, listOf(d), "planned", 100, now, sameDay = false); planned += d }
                        }
                    }
                    val t = usesInLast30Days(plan.uses(db), now)
                    if (t < tally) failures += "seed $seed step $step: the tally fell from $tally to $t"
                    tally = t
                    plan.slots()   // must always be computable (MG17)
                }
                for (id in ids.toList()) { repo.remove(id); removed++ }
                val open = db.tasks().open()
                if (open.any { it.source == "modifier" }) failures += "seed $seed: modifier work left behind: ${open.filter { it.source == "modifier" }.map { it.title }}"
                val offs = db.daysOff().effective().map { it.date }.toSet()
                if (offs != planned.map { it.toString() }.toSet()) failures += "seed $seed: days off are $offs, expected only the ordinary ones ${planned}"
                val finalShape = shape(plan.slots())
                // with no ordinary day off added, removing everything gives the original calendar back
                if (planned.isEmpty() && finalShape != original) failures += "seed $seed: calendar differs from the original after removing everything (${(finalShape - original.toSet()).take(3)} / ${(original - finalShape.toSet()).take(3)})"
                if (finalShape.isEmpty()) failures += "seed $seed: empty calendar"
                if (plan.droppedByChanges() != 0 && ids.isEmpty()) failures += "seed $seed: sessions reported as dropped with no modifier left"
            } catch (e: Throwable) { failures += "seed $seed: ${e::class.simpleName}: ${e.message?.take(200)}" } finally { db.close() }
        }
        println("lifecycle: applied $applied, refused $refused, removed $removed, failures ${failures.size}")
        failures.take(10).forEach { println("  $it") }
        assertTrue(failures.isEmpty(), failures.take(6).joinToString("\n"))
    }
}
