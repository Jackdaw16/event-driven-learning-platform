package io.github.jackdaw16.learningplatform.catalog.infrastructure.persistence;

import io.github.jackdaw16.learningplatform.catalog.domain.CourseLevel;
import io.github.jackdaw16.learningplatform.catalog.domain.CourseStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "courses")
public class CourseJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "estimated_duration_hours", nullable = false)
    private int estimatedDurationHours;

    @Enumerated(EnumType.STRING)
    @Column(name = "level", nullable = false)
    private CourseLevel level;

    @Column(name = "price_amount", nullable = false)
    private BigDecimal priceAmount;

    @Column(name = "currency_code", nullable = false)
    private String currencyCode;

    @Column(name = "maximum_seats", nullable = false)
    private int maximumSeats;

    @Column(name = "occupied_seats", nullable = false)
    private int occupiedSeats;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CourseStatus status;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(name = "instructor_id", nullable = false)
    private UUID instructorId;

    protected CourseJpaEntity() {
    }

    CourseJpaEntity(
            UUID id,
            String title,
            String description,
            int estimatedDurationHours,
            CourseLevel level,
            BigDecimal priceAmount,
            String currencyCode,
            int maximumSeats,
            int occupiedSeats,
            CourseStatus status,
            UUID categoryId,
            UUID instructorId
    ) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.estimatedDurationHours = estimatedDurationHours;
        this.level = level;
        this.priceAmount = priceAmount;
        this.currencyCode = currencyCode;
        this.maximumSeats = maximumSeats;
        this.occupiedSeats = occupiedSeats;
        this.status = status;
        this.categoryId = categoryId;
        this.instructorId = instructorId;
    }

    UUID id() {
        return id;
    }

    String title() {
        return title;
    }

    String description() {
        return description;
    }

    int estimatedDurationHours() {
        return estimatedDurationHours;
    }

    CourseLevel level() {
        return level;
    }

    BigDecimal priceAmount() {
        return priceAmount;
    }

    String currencyCode() {
        return currencyCode;
    }

    int maximumSeats() {
        return maximumSeats;
    }

    int occupiedSeats() {
        return occupiedSeats;
    }

    CourseStatus status() {
        return status;
    }

    UUID categoryId() {
        return categoryId;
    }

    UUID instructorId() {
        return instructorId;
    }
}
