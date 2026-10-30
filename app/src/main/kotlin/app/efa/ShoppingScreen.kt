package app.efa

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.efa.data.ShoppingRow
import efa.Shopping
import efa.pounds

/**
 * The shopping list (SH1 to SH5). Tick items off in the shop, change a price from the receipt (an edited price is no longer an estimate), and see the weekly
 * basket against the food budget. The pantry is bought once and is not part of the weekly budget.
 */
@Composable
fun ShoppingScreen(items: List<ShoppingRow>, onTick: (ShoppingRow) -> Unit, onPrice: (ShoppingRow, Int) -> Unit, onNewShop: () -> Unit, onBack: () -> Unit) {
    val c = LocalColors.current
    val weekly = items.filter { !it.pantry }; val pantry = items.filter { it.pantry }
    val weeklyTotal = weekly.sumOf { it.pricePence }
    Column(
        Modifier.fillMaxSize().background(c.bg).windowInsetsPadding(WindowInsets.systemBars).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Label("SHOPPING")
        BasicText("Shopping list", style = Ts(c.text, 28.sp, FontWeight.Bold))
        BasicText("Prices are from 19 September 2026, so check them against your receipt. Items marked ESTIMATE are not from the original book.", style = Ts(c.muted, 14.sp))
        Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BasicText("Weekly basket ${pounds(weeklyTotal)}", style = Ts(c.text, 20.sp, FontWeight.Bold))
            val room = efa.FOOD_PENCE - weeklyTotal
            BasicText(if (room >= 0) "${pounds(room)} left of the ${pounds(efa.FOOD_PENCE)} food budget." else "${pounds(-room)} over the ${pounds(efa.FOOD_PENCE)} food budget.", style = Ts(c.muted, 14.sp))
            BasicText("${weekly.count { it.ticked }} of ${weekly.size} ticked.", style = Ts(c.muted, 14.sp))
        }
        weekly.forEach { Row(it, onTick, onPrice) }
        Btn("Start a new shop: untick the weekly basket", onNewShop, modifier = Modifier.fillMaxWidth())
        Label("PANTRY, BOUGHT ONCE")
        BasicText("Pantry ${pounds(pantry.sumOf { it.pricePence })}. A one-off cost, not part of the weekly budget.", style = Ts(c.muted, 14.sp))
        pantry.forEach { Row(it, onTick, onPrice) }
        Btn("Back", onBack, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun Row(item: ShoppingRow, onTick: (ShoppingRow) -> Unit, onPrice: (ShoppingRow, Int) -> Unit) {
    val c = LocalColors.current
    Column(Modifier.fillMaxWidth().card().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BasicText(item.name, style = Ts(c.text, 15.sp, FontWeight.Bold))
        BasicText(pounds(item.pricePence) + if (item.estimate) "  ESTIMATE" else "", style = Ts(c.muted, 14.sp))
        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(if (item.ticked) "Ticked" else "Tick off", item.ticked, { onTick(item) }, (if (item.ticked) "Untick " else "Tick off ") + item.name)
            Chip("-10p", false, { onPrice(item, (item.pricePence - 10).coerceAtLeast(1)) }, "Price down 10 pence for ${item.name}")
            Chip("+10p", false, { onPrice(item, item.pricePence + 10) }, "Price up 10 pence for ${item.name}")
        }
    }
}
