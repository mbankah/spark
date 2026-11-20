package app.efa

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import efa.Confirm
import efa.START_REWARD_DAILY_CAP
import efa.START_REWARD_MIN

/** CF1: a question waiting for an answer, on the home screen. One button per option; the same card for every kind of question. */
@Composable
fun ConfirmCard(c: Confirm, onAnswer: (String, String) -> Unit) {
    val col = LocalColors.current
    Column(Modifier.fillMaxWidth().card().padding(16.dp).semantics { contentDescription = "Confirm. ${c.title}. ${c.body}" }, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Label("CONFIRM")
        BasicText(c.title, style = Ts(col.text, 19.sp, FontWeight.Bold))
        BasicText(c.body, style = Ts(col.muted, 14.sp))
        c.options.forEachIndexed { i, o -> Btn(o.label, { onAnswer(c.id, o.id) }, primary = i == 0, modifier = Modifier.fillMaxWidth(), description = o.label) }
    }
}

/** SR1: the line under a started block or cycle that says what the start bought. */
@Composable
fun StartRewardNote() {
    BasicText("Started: $START_REWARD_MIN min of open phone earned (up to $START_REWARD_DAILY_CAP starts a day).", style = Ts(LocalColors.current.muted, 13.sp), modifier = Modifier.semantics { contentDescription = "Started. $START_REWARD_MIN minutes of open phone earned" })
}
