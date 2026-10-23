package efa

/**
 * Social and video apps pre-selected on the first-run block list, from the seed's "Social and video" tier (K1, M6). The person
 * can change the selection once, at setup; later changes go through the wall (K2, M7). Games, shopping and the always-blocked tier
 * of the seed are not pre-selected, because the ledger blocks only the social list and YouTube (Q-C8).
 */
val SOCIAL_AND_VIDEO: Map<String, String> = linkedMapOf(
    "com.zhiliaoapp.musically" to "TikTok",
    "com.instagram.android" to "Instagram",
    "com.snapchat.android" to "Snapchat",
    "com.google.android.youtube" to "YouTube",
    "com.reddit.frontpage" to "Reddit",
    "com.twitter.android" to "X",
    "com.discord" to "Discord",
    "com.netflix.mediaclient" to "Netflix",
    "tv.twitch.android.app" to "Twitch",
    "com.amazon.avod.thirdpartyclient" to "Prime Video",
    "com.facebook.katana" to "Facebook",
    "com.facebook.orca" to "Messenger",
    "com.pinterest" to "Pinterest",
    "org.telegram.messenger" to "Telegram",
    "com.instagram.barcelona" to "Threads",
    "com.bereal.ft" to "BeReal",
)

/** Apps that must never be blockable from the picker's defaults (X1, LA1): calls, messages used for emergencies, maps, banking, authenticator. */
val NEVER_PRESELECTED: Set<String> = setOf(
    "com.android.dialer", "com.google.android.dialer", "com.google.android.apps.maps", "com.google.android.apps.authenticator2",
    "com.monzo.android", "com.starlingbank.android", "com.revolut.revolut", "com.whatsapp",
)

/** The default selection: the social tier apps that are installed, minus anything protected. */
fun defaultBlockList(installed: Collection<String>): Set<String> = installed.filter { it in SOCIAL_AND_VIDEO && it !in NEVER_PRESELECTED }.toSet()
