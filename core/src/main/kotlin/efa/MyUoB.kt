package efa

/**
 * L7: a Check MyUoB button opens the MyUoB app so you can see the university's record. It gates nothing and does not count as proof. Spark cannot read the app, only
 * open it. The package name is not assumed: an installed app whose name says MyUoB (or the university's) is used, and if there is none the button says so.
 */
fun findMyUoB(installed: Collection<Pair<String, String>>): String? =
    installed.firstOrNull { (pkg, label) -> val l = label.lowercase(); l.contains("myuob") || l.contains("my uob") || l.contains("my uo b") || l == "uob" || l.contains("university of birmingham") || pkg.lowercase().contains("myuob") }?.first
