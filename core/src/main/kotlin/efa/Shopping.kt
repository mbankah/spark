package efa

import java.math.BigDecimal

/**
 * The shopping list (section 18: SH1 to SH5). It comes from the Master Plan with prices from 19 September 2026, so every price is a thing to check against a
 * receipt. Items marked ESTIMATE were added later and are not in the original book (SH3).
 */
data class ShopItem(val name: String, val pence: Int, val estimate: Boolean, val pantry: Boolean)

object Shopping {
    val weekly: List<ShopItem> = listOf(
        ShopItem("Chicken thigh and drumstick, about 1.5 kg (8 pieces)", 479, false, false),
        ShopItem("Pork leg steaks, about 600 g", 399, false, false),
        ShopItem("Mackerel fillets, about 300 g", 235, false, false),
        ShopItem("Free-range eggs, 15", 265, false, false),
        ShopItem("Greek-style yogurt, 450 g x 2", 230, false, false),
        ShopItem("Apples, 6-pack", 165, false, false),
        ShopItem("Bananas, 5-pack", 85, false, false),
        ShopItem("Frozen mixed vegetables, 1 kg", 109, false, false),
        ShopItem("Salad bag", 85, false, false),
        ShopItem("Premade fried chicken strips, about 900 g", 450, true, false),
        ShopItem("Cheese singles x 2 packs", 260, true, false),
        ShopItem("Large tortilla wraps x 2 packs", 220, true, false),
        ShopItem("Sweet salad or coleslaw tubs x 3", 210, true, false),
        ShopItem("Sauce ingredients: mayo, sweet chilli, honey, hot sauce", 450, true, false),
    )
    val pantry: List<ShopItem> = listOf(
        ShopItem("Basmati rice, 5 kg", 579, false, true),
        ShopItem("Fusilli or penne, 1 kg x 3", 285, false, true),
        ShopItem("Granola, 750 g x 2", 430, false, true),
        ShopItem("Ground cinnamon", 85, false, true),
        ShopItem("Garlic powder", 95, false, true),
        ShopItem("Onion powder", 95, false, true),
        ShopItem("Ground ginger", 85, false, true),
        ShopItem("Soy sauce, 300 ml", 109, false, true),
        ShopItem("Sweet chilli sauce, 285 ml", 115, false, true),
        ShopItem("Black pepper", 99, false, true),
        ShopItem("Chilli flakes", 75, false, true),
        ShopItem("Mixed herbs", 65, false, true),
        ShopItem("Smoked paprika", 75, false, true),
        ShopItem("Vegetable oil, 1 L", 155, false, true),
        ShopItem("Mild cheddar, 400 g block", 229, false, true),
    )
    val all: List<ShopItem> get() = weekly + pantry

    /** SH4: the weekly basket fits inside the food budget, with the room left over. */
    fun weeklyTotal(items: List<ShopItem> = weekly) = items.sumOf { it.pence }
    fun roomInFoodBudget(items: List<ShopItem> = weekly) = FOOD_PENCE - weeklyTotal(items)

    /** SH5: the pantry is bought once, so it is not part of the weekly budget. */
    fun pantryTotal(items: List<ShopItem> = pantry) = items.sumOf { it.pence }

    /** The first nine weekly items are the original book's list. */
    fun bookOnlyTotal(items: List<ShopItem> = weekly) = items.filter { !it.estimate }.sumOf { it.pence }
}

/** SH3: a price typed from a receipt, "4.79" or "£4.79". Anything else is no price. */
fun parsePricePence(text: String): Int? {
    val t = text.trim().removePrefix("£").trim()
    if (!Regex("^\\d{1,4}(\\.\\d{1,2})?$").matches(t)) return null
    val pence = BigDecimal(t).movePointRight(2).toInt()
    return pence.takeIf { it in 1..99_999 }
}
