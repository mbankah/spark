package efa

import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.*

class DomainsTest {
    private val list = setOf("com.google.android.youtube", "com.instagram.android", "com.reddit.frontpage")
    private val owners = domainOwners(list)

    @Test fun K10_the_host_is_read_from_what_the_address_bar_shows() {
        assertEquals("m.youtube.com", hostOf("https://m.youtube.com/watch?v=abc")); assertEquals("youtube.com", hostOf("youtube.com/feed/subscriptions"))
        assertEquals("www.reddit.com", hostOf("www.reddit.com")); assertEquals("example.com", hostOf("http://user@example.com:8080/x#y"))
    }

    @Test fun K10_search_text_an_empty_bar_and_addresses_with_no_name_have_no_host() {
        assertNull(hostOf(null)); assertNull(hostOf("")); assertNull(hostOf("how to study for exams")); assertNull(hostOf("youtube")); assertNull(hostOf("192.168.1.1"))
        assertNull(hostOf("..com")); assertNull(hostOf("exa mple.com")); assertNull(hostOf("https://"))
    }

    @Test fun K10_a_listed_domain_and_its_subdomains_belong_to_the_app_and_other_sites_do_not() {
        assertEquals("com.google.android.youtube", ownerOfHost("m.youtube.com", owners)); assertEquals("com.google.android.youtube", ownerOfHost("youtube.com", owners))
        assertEquals("com.google.android.youtube", ownerOfHost("youtu.be", owners)); assertEquals("com.reddit.frontpage", ownerOfHost("old.reddit.com", owners))
        assertNull(ownerOfHost("notyoutube.com", owners)); assertNull(ownerOfHost("youtube.com.evil.example", owners)); assertNull(ownerOfHost("mail.google.com", owners))
        assertNull(ownerOfHost(null, owners))
    }

    @Test fun K7_other_company_services_are_never_listed() {
        val all = APP_DOMAINS.values.flatten()
        listOf("google.com", "gmail.com", "maps.google.com", "drive.google.com", "whatsapp.com", "monzo.com", "bristol.ac.uk", "instructure.com").forEach { assertFalse(it in all, it) }
    }

    @Test fun K10_only_the_domains_of_apps_on_the_list_are_blocked() {
        assertNull(ownerOfHost("tiktok.com", owners)); assertNotNull(ownerOfHost("instagram.com", owners))
        assertTrue(domainOwners(emptySet()).isEmpty())
    }

    @Test fun K10_a_website_is_blocked_exactly_when_its_app_is_blocked_and_opens_with_it() {
        val slots = WeekB.slots
        val work = at(LocalDate.of(2026, 11, 2), LocalTime.of(15, 30))           // Monday: a study block
        val app = "com.google.android.youtube"
        val host = ownerOfHost(hostOf("https://m.youtube.com/"), owners)!!
        assertEquals(app, host)
        assertIs<Decision.Block>(decide(host, work, slots, list))
        val allowed = Allowances(appUntil = mapOf(app to work.plusMinutes(20)))
        assertEquals(Decision.Allow, decide(host, work, slots, list, allowed))            // passing the wall opens the website with the app
        val lunch = at(LocalDate.of(2026, 11, 2), LocalTime.of(12, 30))
        assertEquals(Decision.Allow, decide(host, lunch, slots, list))                    // and in a meal both are open
    }

    @Test fun K10_the_browsers_Spark_can_read_are_named_with_their_address_bar() {
        assertEquals("com.android.chrome:id/url_bar", browserFor("com.android.chrome")?.urlBarId); assertNull(browserFor("com.example.unknown"))
        assertTrue(BROWSERS.all { it.urlBarId.startsWith(it.pkg + ":id/") }); assertEquals(BROWSERS.size, BROWSERS.map { it.pkg }.distinct().size)
    }

    @Test fun K10_the_coverage_list_says_plainly_which_installed_browsers_are_not_covered() {
        val c = browserCoverage(listOf("com.example.odd" to "Odd Browser", "com.android.chrome" to "Chrome"))
        assertEquals(listOf("Chrome" to true, "Odd Browser" to false), c.map { it.name to it.covered })
        assertTrue(browserCoverage(emptyList()).isEmpty())
    }

    @Test fun K4_K5_the_hosts_file_lines_cover_the_root_www_and_mobile_forms_of_each_listed_domain() {
        val lines = hostsLines(setOf("com.google.android.youtube"))
        assertEquals(listOf("0.0.0.0 youtu.be", "0.0.0.0 www.youtu.be", "0.0.0.0 m.youtu.be", "0.0.0.0 youtube.com", "0.0.0.0 www.youtube.com", "0.0.0.0 m.youtube.com"), lines)
        assertTrue(hostsLines(emptySet()).isEmpty())
    }
}
