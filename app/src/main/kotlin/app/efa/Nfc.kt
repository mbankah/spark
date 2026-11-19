package app.efa

import android.app.Activity
import android.content.Context
import android.nfc.NfcAdapter
import android.nfc.Tag
import app.efa.data.EfaDb
import app.efa.data.FactRepo
import app.efa.data.META_NFC_ON
import efa.Haptic
import efa.NfcPurpose
import efa.tagKey
import efa.tapFact
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * SL2: NFC tags, an option. It is off by default and photos stay the proof. When it is on and Spark is in front, a tap on a registered tag is one fact of the NFC tier. A tag is known by a hash
 * of its id and mapped to a place (desk, Bible seat, bathroom, charger, front door). Taps are read only while Spark is open, so nothing runs in the background.
 */
object Nfc {
    /** The place the next tap registers, or null. */
    @Volatile var registering: NfcPurpose? = null
    /** The last message, for the screen to show. */
    @Volatile var message: String? = null

    fun available(ctx: Context): Boolean = NfcAdapter.getDefaultAdapter(ctx) != null

    fun start(activity: Activity) {
        val adapter = NfcAdapter.getDefaultAdapter(activity) ?: return
        val db = EfaDb.get(activity.applicationContext)
        CoroutineScope(Dispatchers.IO).launch {
            if (db.meta().get(META_NFC_ON) != "1") return@launch
            try {
                adapter.enableReaderMode(activity, { tag -> handle(activity.applicationContext, tag) },
                    NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V or NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS, null)
            } catch (e: Exception) { /* X7: a phone that refuses reader mode just has no tags */ }
        }
    }

    fun stop(activity: Activity) {
        try { NfcAdapter.getDefaultAdapter(activity)?.disableReaderMode(activity) } catch (e: Exception) { }
    }

    private fun handle(ctx: Context, tag: Tag) {
        CoroutineScope(Dispatchers.IO).launch {
            val repo = FactRepo(EfaDb.get(ctx)); val key = tagKey(tag.id)
            val reg = registering
            if (reg != null) {
                repo.saveTags(repo.tags().filterValues { it != reg } + (key to reg)); registering = null
                message = "${reg.label} tag registered."; Coach.haptic(ctx, Haptic.START); return@launch
            }
            val purpose = repo.tags()[key]
            if (purpose == null) { message = "That tag is not registered. Register it first."; return@launch }
            repo.record(tapFact(purpose, Instant.now(), atItsPlace = true))
            message = "${purpose.label} tag read."; Coach.haptic(ctx, Haptic.REWARD)
        }
    }
}
