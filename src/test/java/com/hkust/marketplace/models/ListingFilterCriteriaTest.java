package com.hkust.marketplace.models;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ListingFilterCriteria & ListingSortOrder Model Tests")
class ListingFilterCriteriaTest {

    @Test
    @DisplayName("Default criteria has no active filters and defaults to NEWEST_FIRST")
    void testDefaultCriteria() {
        ListingFilterCriteria criteria = new ListingFilterCriteria();
        assertNull(criteria.getKeyword());
        assertNull(criteria.getCategory());
        assertNull(criteria.getType());
        assertNull(criteria.getCondition());
        assertNull(criteria.getMinPrice());
        assertNull(criteria.getMaxPrice());
        assertEquals(ListingSortOrder.NEWEST_FIRST, criteria.getSortOrder());
        assertFalse(criteria.hasActiveFilters());
        assertTrue(criteria.isPriceRangeValid());

        ListingFilterCriteria allCriteria = ListingFilterCriteria.all();
        assertFalse(allCriteria.hasActiveFilters());
        assertEquals(criteria, allCriteria);
    }

    @Test
    @DisplayName("Builder and full constructor populate all fields correctly")
    void testBuilderAndFullConstructor() {
        ListingFilterCriteria built = ListingFilterCriteria.builder()
                .keyword("laptop")
                .category(Category.ELECTRONICS)
                .type(ListingType.SELL)
                .condition(Condition.LIKE_NEW)
                .minPrice(100.0)
                .maxPrice(500.0)
                .sortOrder(ListingSortOrder.PRICE_LOW_TO_HIGH)
                .build();

        assertEquals("laptop", built.getKeyword());
        assertEquals(Category.ELECTRONICS, built.getCategory());
        assertEquals(ListingType.SELL, built.getType());
        assertEquals(Condition.LIKE_NEW, built.getCondition());
        assertEquals(100.0, built.getMinPrice());
        assertEquals(500.0, built.getMaxPrice());
        assertEquals(ListingSortOrder.PRICE_LOW_TO_HIGH, built.getSortOrder());
        assertTrue(built.hasActiveFilters());
        assertTrue(built.isPriceRangeValid());

        ListingFilterCriteria direct = new ListingFilterCriteria("laptop", Category.ELECTRONICS,
                ListingType.SELL, Condition.LIKE_NEW, 100.0, 500.0, ListingSortOrder.PRICE_LOW_TO_HIGH);
        assertEquals(built, direct);
        assertEquals(built.hashCode(), direct.hashCode());
        assertTrue(built.toString().contains("laptop"));
    }

    @Test
    @DisplayName("hasActiveFilters detects every individual filter dimension")
    void testHasActiveFiltersDimensions() {
        ListingFilterCriteria c = new ListingFilterCriteria();
        assertFalse(c.hasActiveFilters());

        c.setKeyword("   ");
        assertFalse(c.hasActiveFilters());
        c.setKeyword("macbook");
        assertTrue(c.hasActiveFilters());
        c.setKeyword(null);

        c.setCategory(Category.BOOKS);
        assertTrue(c.hasActiveFilters());
        c.setCategory(null);

        c.setType(ListingType.LEND);
        assertTrue(c.hasActiveFilters());
        c.setType(null);

        c.setCondition(Condition.NEW);
        assertTrue(c.hasActiveFilters());
        c.setCondition(null);

        c.setMinPrice(50.0);
        assertTrue(c.hasActiveFilters());
        c.setMinPrice(null);

        c.setMaxPrice(200.0);
        assertTrue(c.hasActiveFilters());
        c.setMaxPrice(null);

        assertFalse(c.hasActiveFilters());
    }

    @Test
    @DisplayName("Price range validation rules: negative values and inverted bounds")
    void testPriceRangeValidation() {
        ListingFilterCriteria c = new ListingFilterCriteria();
        assertTrue(c.isPriceRangeValid());

        c.setMinPrice(10.0);
        c.setMaxPrice(100.0);
        assertTrue(c.isPriceRangeValid());

        // Min equal to max is valid
        c.setMinPrice(50.0);
        c.setMaxPrice(50.0);
        assertTrue(c.isPriceRangeValid());

        // Inverted: min > max
        c.setMinPrice(100.0);
        c.setMaxPrice(50.0);
        assertFalse(c.isPriceRangeValid());

        // Negative min
        c.setMinPrice(-1.0);
        c.setMaxPrice(50.0);
        assertFalse(c.isPriceRangeValid());

        // Negative max
        c.setMinPrice(10.0);
        c.setMaxPrice(-5.0);
        assertFalse(c.isPriceRangeValid());
    }

    @Test
    @DisplayName("Reset restores criteria to initial clean state")
    void testReset() {
        ListingFilterCriteria c = ListingFilterCriteria.builder()
                .keyword("test")
                .category(Category.SPORTS)
                .type(ListingType.GIVEAWAY)
                .condition(Condition.FOR_PARTS)
                .minPrice(10.0)
                .maxPrice(20.0)
                .sortOrder(ListingSortOrder.PRICE_HIGH_TO_LOW)
                .build();

        assertTrue(c.hasActiveFilters());
        c.reset();

        assertFalse(c.hasActiveFilters());
        assertNull(c.getKeyword());
        assertNull(c.getCategory());
        assertNull(c.getType());
        assertNull(c.getCondition());
        assertNull(c.getMinPrice());
        assertNull(c.getMaxPrice());
        assertEquals(ListingSortOrder.NEWEST_FIRST, c.getSortOrder());
    }

    @Test
    @DisplayName("ListingSortOrder fromString and toString coverage")
    void testListingSortOrder() {
        assertEquals(ListingSortOrder.NEWEST_FIRST, ListingSortOrder.fromString("Newest First"));
        assertEquals(ListingSortOrder.NEWEST_FIRST, ListingSortOrder.fromString("NEWEST_FIRST"));
        assertEquals(ListingSortOrder.PRICE_LOW_TO_HIGH, ListingSortOrder.fromString("Price: Low to High"));
        assertEquals(ListingSortOrder.PRICE_LOW_TO_HIGH, ListingSortOrder.fromString("PRICE_LOW_TO_HIGH"));
        assertEquals(ListingSortOrder.PRICE_HIGH_TO_LOW, ListingSortOrder.fromString("Price: High to Low"));
        assertEquals(ListingSortOrder.PRICE_HIGH_TO_LOW, ListingSortOrder.fromString("PRICE_HIGH_TO_LOW"));

        // Fallbacks
        assertEquals(ListingSortOrder.NEWEST_FIRST, ListingSortOrder.fromString(null));
        assertEquals(ListingSortOrder.NEWEST_FIRST, ListingSortOrder.fromString("UNKNOWN_ORDER"));

        assertEquals("Newest First", ListingSortOrder.NEWEST_FIRST.toString());
        assertEquals("Price: Low to High", ListingSortOrder.PRICE_LOW_TO_HIGH.getDisplayName());
    }

    @Test
    @DisplayName("Equals and hashCode contracts")
    void testEqualsAndHashCode() {
        ListingFilterCriteria c1 = ListingFilterCriteria.builder().keyword("A").build();
        ListingFilterCriteria c2 = ListingFilterCriteria.builder().keyword("A").build();
        ListingFilterCriteria c3 = ListingFilterCriteria.builder().keyword("B").build();

        assertEquals(c1, c1);
        assertEquals(c1, c2);
        assertNotEquals(c1, c3);
        assertNotEquals(c1, null);
        assertNotEquals(c1, "NotACriteria");
        assertEquals(c1.hashCode(), c2.hashCode());
    }
}
