package efa

import kotlin.test.*

/** WK1 to WK6: the walkthrough says what Spark does, in the voice of the manual, from the rules' own numbers, and is there again whenever you want it. */
class WalkthroughTest {
    private val manual = java.io.File("../docs/MANUAL.md").readText().replace("\r\n", "\n")
    private val pages = WALKTHROUGH
    private fun text(p: WalkPage) = (listOf(p.label, p.title) + p.lines)

    @Test fun WK1_it_opens_by_itself_until_it_has_been_seen_once_and_never_when_a_build_turns_it_off() {
        assertTrue(walkthroughDue(null)); assertTrue(walkthroughDue("")); assertTrue(walkthroughDue("0"))
        assertFalse(walkthroughDue(WALKTHROUGH_SEEN_VALUE), "seen once: it does not open again by itself")
        assertFalse(walkthroughDue(null, enforced = false), "a test build turns it off")
    }

    @Test fun WK2_it_is_nine_short_pages_one_art_each_beginning_with_the_idea_and_ending_with_what_to_do_first() {
        assertEquals(9, pages.size); assertEquals(pages.size, pages.map { it.id }.toSet().size)
        assertEquals(WalkArt.entries.toSet(), pages.map { it.art }.toSet()); assertEquals(pages.size, pages.map { it.art }.toSet().size, "each page has its own picture")
        assertEquals(WalkArt.SPARK, pages.first().art); assertEquals(WalkArt.START, pages.last().art)
        assertTrue(pages.first().title.startsWith("Friction for your intentions"))
    }

    @Test fun WK6_every_word_follows_the_voice_rules_and_carries_no_motivation() {
        val motivational = listOf("amazing", "great", "awesome", "journey", "goal", "streak", "crush", "level up", "congrat", "proud", "motivat", "inspir", "unlock your")
        for (p in pages) for (t in text(p)) {
            assertEquals(emptyList(), voiceProblems(t), t)
            for (w in motivational) assertFalse(t.lowercase().contains(w), "$w in: $t")
        }
    }

    @Test fun WK7_it_is_short_enough_to_read_in_about_two_minutes_and_each_page_is_a_few_short_lines() {
        val words = pages.sumOf { p -> text(p).sumOf { it.split(" ").size } }
        assertTrue(words <= 430, "$words words")
        for (p in pages) {
            assertTrue(p.title.length <= 56, p.title); assertTrue(p.lines.size in 2..3, p.id)
            for (l in p.lines) assertTrue(l.length <= 150, "${p.id}: $l")
        }
    }

    @Test fun WK5_the_numbers_come_from_the_rules_so_a_change_to_a_rule_changes_the_page() {
        val phone = pages.first { it.id == "phone" }.lines.joinToString(" "); val a = AppCap()
        assertTrue("${a.windowMin} minutes at a time" in phone && "${a.cooldownMin} minute cooldown" in phone)
        val wall = pages.first { it.id == "wall" }.lines.joinToString(" "); val l = ChangeLimits()
        assertTrue("$WALL_BASE characters" in wall && "${l.freeImportsPerWeek} imports and ${l.freeMovesPerWeek} moves" in wall && "$BREAK_GLASS_WALL characters" in wall)
        val changed = walkthroughPages(cap = AppCap(windowMin = 7, cooldownMin = 45), limits = ChangeLimits(freeMovesPerWeek = 9), wallBase = 90, glassWall = 200)
        assertTrue("7 minutes at a time" in changed.first { it.id == "phone" }.lines.joinToString(" ")); assertTrue("9 moves" in changed.first { it.id == "wall" }.lines.joinToString(" "))
    }

    @Test fun WK6_it_says_what_android_does_not_let_spark_do_and_never_promises_more_than_the_app_does() {
        val yours = pages.first { it.id == "yours" }.lines.joinToString(" ")
        assertTrue("cannot prevent" in yours && "notices" in yours)
        val all = pages.flatMap { text(it) }.joinToString(" ").lowercase()
        for (w in listOf("guarantee", "always works", "cannot fail", "impossible to", "unbreakable")) assertFalse(w in all, w)
        assertTrue("never blocked" in all && "never cut off" in all)
    }

    @Test fun WK4_the_manual_says_it_opens_on_first_use_and_where_to_open_it_again() {
        assertTrue("Walkthrough" in manual && "Setup" in manual.substringAfter("Walkthrough").take(400), "MANUAL.md describes the walkthrough and where it lives")
    }
}
