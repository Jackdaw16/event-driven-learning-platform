package io.github.jackdaw16.learningplatform.catalog.domain;

import io.github.jackdaw16.learningplatform.shared.Money;
import java.util.Objects;
import java.util.UUID;

public final class Course {

    private final UUID id;
    private final String title;
    private final String description;
    private final int estimatedDurationHours;
    private final CourseLevel level;
    private final Money price;
    private final int maximumSeats;
    private final UUID categoryId;
    private final UUID instructorId;
    private int occupiedSeats;
    private CourseStatus status;

    public Course(
            UUID id,
            String title,
            String description,
            int estimatedDurationHours,
            CourseLevel level,
            Money price,
            int maximumSeats,
            UUID categoryId,
            UUID instructorId
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        this.title = title;
        this.description = description;
        if (estimatedDurationHours <= 0) {
            throw new IllegalArgumentException("estimated duration hours must be positive");
        }
        this.estimatedDurationHours = estimatedDurationHours;
        this.level = Objects.requireNonNull(level, "level must not be null");
        this.price = Objects.requireNonNull(price, "price must not be null");
        if (maximumSeats <= 0) {
            throw new IllegalArgumentException("maximum seats must be positive");
        }
        this.maximumSeats = maximumSeats;
        this.categoryId = Objects.requireNonNull(categoryId, "category id must not be null");
        this.instructorId = Objects.requireNonNull(instructorId, "instructor id must not be null");
        this.occupiedSeats = 0;
        this.status = CourseStatus.DRAFT;
    }

    public UUID id() {
        return id;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    public int estimatedDurationHours() {
        return estimatedDurationHours;
    }

    public CourseLevel level() {
        return level;
    }

    public Money price() {
        return price;
    }

    public int maximumSeats() {
        return maximumSeats;
    }

    public int occupiedSeats() {
        return occupiedSeats;
    }

    public CourseStatus status() {
        return status;
    }

    public UUID categoryId() {
        return categoryId;
    }

    public UUID instructorId() {
        return instructorId;
    }

    public void publish() {
        if (status != CourseStatus.DRAFT) {
            throw new IllegalStateException("Only draft courses can be published");
        }
        status = CourseStatus.PUBLISHED;
    }

    public void archive() {
        if (status == CourseStatus.ARCHIVED) {
            throw new IllegalStateException("Course is already archived");
        }
        status = CourseStatus.ARCHIVED;
    }

    public void reserveSeat() {
        if (status != CourseStatus.PUBLISHED) {
            throw new CourseNotPublishedException();
        }
        if (occupiedSeats == maximumSeats) {
            throw new CourseFullException();
        }
        occupiedSeats++;
    }

    public void releaseSeat() {
        if (occupiedSeats == 0) {
            throw new CourseHasNoOccupiedSeatsException();
        }
        occupiedSeats--;
    }
}
