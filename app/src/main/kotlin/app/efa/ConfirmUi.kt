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

/** One button of a question: its words, what a screen reader says, and what it does. */
class AskOption(val label: String, val description: String, val onClick: () -> Unit)

/**
 * The Questions system (CF1): one card for everything that is waiting for your answer, whatever raised it. A kind of question is a label, a title,
 * a line of words and up to three buttons. Confirm questions, the meal question, the estimate question, held notifications and a room Spark cannot place are all this card.
 */
@Composable
fun QuestionCard(label: String, title: String, body: String?, options: List<AskOption>) {
    val col = LocalColors.current
    Column(Modifier.fillMaxWidth().card().padding(16.dp).semantics { contentDescription = "$label. $title." + (body?.takeIf { it.isNotBlank() }?.let { " $it" } ?: "") }, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Label(label)
        BasicText(title, style = Ts(col.text, 19.sp, FontWeight.Bold))
        body?.takeIf { it.isNotBlank() }?.let { BasicText(it, style = Ts(col.muted, 14.sp)) }
        options.forEachIndexed { i, o -> Btn(o.label, o.onClick, primary = i == 0, modifier = Modifier.fillMaxWidth(), description = o.description) }
    }
}

/** CF1: a question waiting for an answer, on the home screen. One button per option; the same card for every kind of question. */
@Composable
fun ConfirmCard(c: Confirm, onAnswer: (String, String) -> Unit) =
    QuestionCard("CONFIRM", c.title, c.body, c.options.map { o -> AskOption(o.label, o.label) { onAnswer(c.id, o.id) } })

/** SR1: the line under a started block or cycle that says what the start bought. */
@Composable
fun StartRewardNote() {
    BasicText("Started: $START_REWARD_MIN min of open phone earned (up to $START_REWARD_DAILY_CAP starts a day).", style = Ts(LocalColors.current.muted, 13.sp), modifier = Modifier.semantics { contentDescription = "Started. $START_REWARD_MIN minutes of open phone earned" })
}
