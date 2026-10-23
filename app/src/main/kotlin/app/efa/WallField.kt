package app.efa

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import efa.WallEntry

/** No cut, copy or paste menu: the wall is typed by hand (M7). */
private object NoToolbar : TextToolbar {
    override val status = TextToolbarStatus.Hidden
    override fun hide() {}
    override fun showMenu(rect: Rect, onCopyRequested: (() -> Unit)?, onPasteRequested: (() -> Unit)?, onCutRequested: (() -> Unit)?, onSelectAllRequested: (() -> Unit)?) {}
}

/**
 * The effort wall (M7, N3, K2, LA9): a fresh random string shown here, typed by hand. One character at a time is accepted, so a
 * paste or autofill (many characters at once) restarts it, as does any wrong character. There is no paste menu, no suggestions and
 * no autocorrect.
 * Limit, stated plainly: a keyboard app or automation that sends one character at a time can still type it. Android cannot prevent
 * that (docs/DECISIONS.md D-12).
 */
@Composable
fun WallField(target: String, onDone: () -> Unit) {
    val c = LocalColors.current
    val entry = remember(target) { WallEntry(target) }
    var typed by remember(target) { mutableIntStateOf(0) }
    var restarts by remember(target) { mutableIntStateOf(0) }
    var done by remember(target) { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(target) { focus.requestFocus() }          // the keyboard opens at once: starting the wall is not made harder

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Label("TYPE THIS EXACTLY, BY HAND ($typed OF ${target.length})")
        // The string, in lines of 15, with the part already typed in full colour.
        Column(Modifier.fillMaxWidth().background(c.bg, CardShape).padding(12.dp).semantics { contentDescription = "Characters to type: $target" }) {
            target.chunked(15).forEachIndexed { i, line ->
                val from = i * 15
                val ok = (typed - from).coerceIn(0, line.length)
                BasicText(
                    androidx.compose.ui.text.buildAnnotatedString {
                        pushStyle(androidx.compose.ui.text.SpanStyle(color = c.text, fontWeight = FontWeight.Bold)); append(line.take(ok)); pop()
                        pushStyle(androidx.compose.ui.text.SpanStyle(color = c.muted)); append(line.drop(ok)); pop()
                    },
                    style = Ts(c.text, 20.sp, fontFamily = Mono, letterSpacing = 1.sp),
                )
            }
        }
        CompositionLocalProvider(LocalTextToolbar provides NoToolbar) {
            val shown = target.take(typed)
            BasicTextField(
                value = TextFieldValue(shown, TextRange(shown.length)),
                onValueChange = { nv ->
                    if (done) return@BasicTextField
                    val grew = nv.text.length - shown.length
                    when {
                        grew == 1 && nv.text.startsWith(shown) -> {
                            val before = entry.restarts
                            entry.input(nv.text.last().toString()); typed = entry.typed
                            if (entry.restarts != before) restarts++
                        }
                        grew > 1 -> { entry.input(nv.text.drop(shown.length)); typed = entry.typed; restarts++ }     // paste or autofill
                        else -> {}                                                                                        // deleting is not allowed
                    }
                    if (entry.done) { done = true; onDone() }
                },
                singleLine = true,
                textStyle = Ts(c.text, 18.sp, fontFamily = Mono),
                cursorBrush = SolidColor(c.text),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = KeyboardType.Password, imeAction = ImeAction.None),
                modifier = Modifier.fillMaxWidth().focusRequester(focus).card().padding(14.dp).semantics { contentDescription = "Type the characters here" },
            )
        }
        if (restarts > 0) BasicText("Mistake. Start again from the first character.", style = Ts(c.text, 14.sp, FontWeight.SemiBold))
    }
}
