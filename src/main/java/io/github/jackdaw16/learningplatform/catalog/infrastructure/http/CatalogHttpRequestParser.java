package io.github.jackdaw16.learningplatform.catalog.infrastructure.http;

import io.github.jackdaw16.learningplatform.catalog.application.CourseSearchCriteria;
import io.github.jackdaw16.learningplatform.catalog.application.PageQuery;
import io.github.jackdaw16.learningplatform.catalog.application.SortDirection;
import io.github.jackdaw16.learningplatform.catalog.domain.CourseLevel;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.Set;
import java.util.UUID;

final class CatalogHttpRequestParser {

    private CatalogHttpRequestParser() {
    }

    static PageQuery pageQuery(
            int page,
            int size,
            String sort,
            String defaultSortField,
            Set<String> supportedSortFields
    ) {
        if (sort == null) {
            return new PageQuery(page, size, defaultSortField, SortDirection.ASC);
        }

        String[] components = sort.split(",", -1);
        if (components.length != 2 || !supportedSortFields.contains(components[0])) {
            throw new IllegalArgumentException("Invalid sort parameter");
        }

        return new PageQuery(page, size, components[0], parseSortDirection(components[1]));
    }

    static CourseSearchCriteria courseSearchCriteria(
            String categoryId,
            String level,
            String currency,
            String minPrice,
            String maxPrice,
            String title,
            String availableOnly
    ) {
        return new CourseSearchCriteria(
                parseUuid(categoryId),
                parseLevel(level),
                parseCurrency(currency),
                parseDecimal(minPrice),
                parseDecimal(maxPrice),
                title,
                parseBoolean(availableOnly)
        );
    }

    static Currency currency(String currency) {
        return parseCurrency(currency);
    }

    private static SortDirection parseSortDirection(String value) {
        if ("asc".equalsIgnoreCase(value)) {
            return SortDirection.ASC;
        }
        if ("desc".equalsIgnoreCase(value)) {
            return SortDirection.DESC;
        }
        throw new IllegalArgumentException("Invalid sort parameter");
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return UUID.fromString(value);
    }

    private static CourseLevel parseLevel(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return CourseLevel.valueOf(value);
    }

    private static Currency parseCurrency(String value) {
        if (value == null) {
            return null;
        }
        return Currency.getInstance(value);
    }

    private static BigDecimal parseDecimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return new BigDecimal(value);
    }

    private static Boolean parseBoolean(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        throw new IllegalArgumentException("Invalid boolean parameter");
    }
}
