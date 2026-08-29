package io.github.jackdaw16.learningplatform.catalog.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.Currency;
import org.junit.jupiter.api.Test;

class CourseSearchCriteriaTest {

    @Test
    void rejectsInvalidPriceBounds() {
        Currency usd = Currency.getInstance("USD");

        assertThrows(
                IllegalArgumentException.class,
                () -> new CourseSearchCriteria(null, null, usd, new BigDecimal("-0.01"), null, null, null)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new CourseSearchCriteria(null, null, usd, null, new BigDecimal("-0.01"), null, null)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new CourseSearchCriteria(
                        null, null, usd, new BigDecimal("10.00"), new BigDecimal("9.99"), null, null
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new CourseSearchCriteria(null, null, null, new BigDecimal("10.00"), null, null, null)
        );
    }

    @Test
    void normalizesBlankTitleAndRetainsCurrencyOnlyFilter() {
        Currency eur = Currency.getInstance("EUR");

        CourseSearchCriteria criteria = new CourseSearchCriteria(null, null, eur, null, null, " \t", null);

        assertEquals(eur, criteria.currency());
        assertNull(criteria.title());
    }
}
