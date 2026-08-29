package io.github.jackdaw16.learningplatform.catalog.application;

import io.github.jackdaw16.learningplatform.catalog.domain.CourseLevel;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.UUID;

public record CourseSearchCriteria(
        UUID categoryId,
        CourseLevel level,
        Currency currency,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        String title,
        Boolean availableOnly
) {

    public CourseSearchCriteria {
        if (minPrice != null && minPrice.signum() < 0) {
            throw new IllegalArgumentException("minimum price must not be negative");
        }
        if (maxPrice != null && maxPrice.signum() < 0) {
            throw new IllegalArgumentException("maximum price must not be negative");
        }
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new IllegalArgumentException("minimum price must not exceed maximum price");
        }
        if ((minPrice != null || maxPrice != null) && currency == null) {
            throw new IllegalArgumentException("currency is required when filtering by price");
        }
        if (title != null && title.isBlank()) {
            title = null;
        }
    }
}
