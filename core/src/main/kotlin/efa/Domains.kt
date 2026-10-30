package efa

/**
 * Blocked domains (K4, K7, K10). The same list of sites is blocked in phone browsers (read from the address bar), in the PC browser extension and in the Windows
 * service's hosts file. A domain belongs to the app it is the web version of, so letting an app through the wall (LA9) lets its website through too.
 *
 * Limits, stated plainly (K8, K10): an unusual browser, an in-app web view, a mirror site, encrypted browser DNS and a VPN can all slip past. The aim is to make
 * bypassing expensive, not impossible.
 */

/** K7: listed carefully. A site's subdomains (www, m, music) are covered by the root, but other companies' services are never listed, so Gmail or Maps are not broken. */
val APP_DOMAINS: Map<String, List<String>> = linkedMapOf(
    "com.zhiliaoapp.musically" to listOf("tiktok.com"),
    "com.instagram.android" to listOf("instagram.com"),
    "com.snapchat.android" to listOf("snapchat.com"),
    "com.google.android.youtube" to listOf("youtube.com", "youtu.be"),
    "com.reddit.frontpage" to listOf("reddit.com", "redd.it"),
    "com.twitter.android" to listOf("x.com", "twitter.com"),
    "com.discord" to listOf("discord.com", "discord.gg"),
    "com.netflix.mediaclient" to listOf("netflix.com"),
    "tv.twitch.android.app" to listOf("twitch.tv"),
    "com.amazon.avod.thirdpartyclient" to listOf("primevideo.com"),
    "com.facebook.katana" to listOf("facebook.com", "fb.com"),
    "com.facebook.orca" to listOf("messenger.com"),
    "com.pinterest" to listOf("pinterest.com", "pinterest.co.uk"),
    "org.telegram.messenger" to listOf("telegram.org", "t.me"),
    "com.instagram.barcelona" to listOf("threads.net", "threads.com"),
    "com.bereal.ft" to listOf("bereal.com"),
)

/** Domain to the app it belongs to, for the apps on the block list. */
fun domainOwners(list: Set<String>): Map<String, String> =
    APP_DOMAINS.filterKeys { it in list }.flatMap { (pkg, domains) -> domains.map { it to pkg } }.toMap()

/**
 * The host in what an address bar shows: "https://m.youtube.com/watch?v=1", "youtube.com/feed" or "www.reddit.com". Search text, an empty bar, a number-only
 * address and anything with spaces have no host (null), so typing a word never blocks anything.
 */
fun hostOf(addressBar: String?): String? {
    var t = addressBar?.trim()?.lowercase() ?: return null
    if (t.isEmpty() || t.contains(' ')) return null
    t = t.replace(Regex("^[a-z][a-z0-9+.-]*://"), "")
    t = t.substringBefore('/').substringBefore('?').substringBefore('#').substringBefore(':').substringAfterLast('@')
    if (!t.contains('.') || !Regex("^[a-z0-9.-]+$").matches(t) || t.startsWith(".") || t.endsWith(".") || t.contains("..")) return null
    if (Regex("^[0-9.]+$").matches(t)) return null
    return t
}

/** The app that owns [host], if it is one of the listed domains or a subdomain of one. */
fun ownerOfHost(host: String?, owners: Map<String, String>): String? {
    if (host == null) return null
    return owners.entries.firstOrNull { (domain, _) -> host == domain || host.endsWith(".$domain") }?.value
}

/** A browser Spark knows how to read, and the id of its address bar (K10). */
data class Browser(val pkg: String, val name: String, val urlBarId: String)

val BROWSERS: List<Browser> = listOf(
    Browser("com.android.chrome", "Chrome", "com.android.chrome:id/url_bar"),
    Browser("org.mozilla.firefox", "Firefox", "org.mozilla.firefox:id/mozac_browser_toolbar_url_view"),
    Browser("com.sec.android.app.sbrowser", "Samsung Internet", "com.sec.android.app.sbrowser:id/location_bar_edit_text"),
    Browser("com.brave.browser", "Brave", "com.brave.browser:id/url_bar"),
    Browser("com.microsoft.emmx", "Edge", "com.microsoft.emmx:id/url_bar"),
    Browser("com.duckduckgo.mobile.android", "DuckDuckGo", "com.duckduckgo.mobile.android:id/omnibarTextInput"),
    Browser("com.opera.browser", "Opera", "com.opera.browser:id/url_field"),
    Browser("com.vivaldi.browser", "Vivaldi", "com.vivaldi.browser:id/url_bar"),
    Browser("com.kiwibrowser.browser", "Kiwi", "com.kiwibrowser.browser:id/url_bar"),
)

fun browserFor(pkg: String): Browser? = BROWSERS.firstOrNull { it.pkg == pkg }

/** K10: "the build must test the browsers you actually use". Which installed browsers Spark can read, and which it cannot. */
data class BrowserCoverage(val pkg: String, val name: String, val covered: Boolean)

fun browserCoverage(installedBrowsers: Collection<Pair<String, String>>): List<BrowserCoverage> =
    installedBrowsers.map { (pkg, label) -> BrowserCoverage(pkg, browserFor(pkg)?.name ?: label, browserFor(pkg) != null) }.sortedWith(compareBy({ !it.covered }, { it.name }))

/**
 * K4, K5, K7: the hosts file lines for the PC, one for each blocked domain and for its www and m forms (a hosts file has no wildcards). The Windows service
 * writes these during work blocks and removes them afterwards.
 */
fun hostsLines(list: Set<String>): List<String> =
    domainOwners(list).keys.sorted().flatMap { d -> listOf(d, "www.$d", "m.$d") }.distinct().map { "0.0.0.0 $it" }
