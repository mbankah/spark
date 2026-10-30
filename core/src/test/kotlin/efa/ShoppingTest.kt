package efa

import kotlin.test.*

class ShoppingTest {
    @Test fun SH4_the_weekly_basket_is_36_42_and_the_first_nine_items_are_20_52() {
        assertEquals(3642, Shopping.weeklyTotal()); assertEquals(2052, Shopping.bookOnlyTotal()); assertEquals(14, Shopping.weekly.size)
    }

    @Test fun SH4_the_weekly_basket_fits_the_forty_pound_food_budget_with_3_58_to_spare() {
        assertEquals(4000, FOOD_PENCE); assertEquals(358, Shopping.roomInFoodBudget())
    }

    @Test fun SH5_the_pantry_is_25_76_bought_once_and_is_not_in_the_weekly_total() {
        assertEquals(2576, Shopping.pantryTotal()); assertEquals(15, Shopping.pantry.size)
        assertTrue(Shopping.weekly.none { it.pantry }); assertTrue(Shopping.pantry.all { it.pantry })
    }

    @Test fun SH3_the_five_items_added_later_are_marked_as_estimates_and_the_book_items_are_not() {
        assertEquals(5, Shopping.weekly.count { it.estimate }); assertTrue(Shopping.pantry.none { it.estimate })
    }

    @Test fun SH3_a_price_from_a_receipt_is_read_with_or_without_the_pound_sign() {
        assertEquals(479, parsePricePence("4.79")); assertEquals(479, parsePricePence("£4.79")); assertEquals(500, parsePricePence("5")); assertEquals(450, parsePricePence("4.5"))
        assertNull(parsePricePence("")); assertNull(parsePricePence("free")); assertNull(parsePricePence("0")); assertNull(parsePricePence("4.799")); assertNull(parsePricePence("-3"))
    }

    @Test fun SH1_every_item_has_a_name_and_a_price() {
        assertTrue(Shopping.all.all { it.name.isNotBlank() && it.pence > 0 }); assertEquals(Shopping.all.size, Shopping.all.map { it.name }.distinct().size)
    }
}
