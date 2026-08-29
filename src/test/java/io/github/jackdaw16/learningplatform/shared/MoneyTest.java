package io.github.jackdaw16.learningplatform.shared;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.Currency;
import org.junit.jupiter.api.Test;

class MoneyTest {

    private static final Currency USD = Currency.getInstance("USD");

    @Test
    void allowsFreeCoursesButRejectsNegativeAmounts() {
        assertDoesNotThrow(() -> new Money(BigDecimal.ZERO, USD));
        assertThrows(IllegalArgumentException.class, () -> new Money(new BigDecimal("-0.01"), USD));
    }

    @Test
    void requiresAnAmountAndCurrency() {
        assertThrows(NullPointerException.class, () -> new Money(null, USD));
        assertThrows(NullPointerException.class, () -> new Money(BigDecimal.ZERO, null));
    }
}
