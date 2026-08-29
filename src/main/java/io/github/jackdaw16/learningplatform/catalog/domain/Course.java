package io.github.jackdaw16.learningplatform.catalog.domain;

import io.github.jackdaw16.learningplatform.shared.Money;
import java.util.Objects;
import java.util.UUID;

public final class Course {

    private final UUID id;
    private String title;
    private String description;
    private int estimatedDurationHours;
    private CourseLevel level;
    private Money price;
    private int maximumSeats;
    private UUID categoryId;
    private UUID instructorId;
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
        this(
                id,
                title,
                description,
                estimatedDurationHours,
                level,
                price,
                maximumSeats,
                0,
                CourseStatus.DRAFT,
                categoryId,
                instructorId
        );
    }

    private Course(
            UUID id,
            String title,
            String description,
            int estimatedDurationHours,
            CourseLevel level,
            Money price,
            int maximumSeats,
            int occupiedSeats,
            CourseStatus status,
            UUID categoryId,
            UUID instructorId
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        validateCourseDetails(title, estimatedDurationHours, level, price, maximumSeats, categoryId, instructorId);
        validateOccupancy(occupiedSeats, maximumSeats, status);
        this.title = title;
        this.description = description;
        this.estimatedDurationHours = estimatedDurationHours;
        this.level = level;
        this.price = price;
        this.maximumSeats = maximumSeats;
        this.categoryId = categoryId;
        this.instructorId = instructorId;
        this.occupiedSeats = occupiedSeats;
        this.status = status;
    }

    public static Course rehydrate(
            UUID id,
            String title,
            String description,
            int estimatedDurationHours,
            CourseLevel level,
            Money price,
            int maximumSeats,
            int occupiedSeats,
            CourseStatus status,
            UUID categoryId,
            UUID instructorId
    ) {
        return new Course(
                id,
                title,
                description,
                estimatedDurationHours,
                level,
                price,
                maximumSeats,
                occupiedSeats,
                status,
                categoryId,
                instructorId
        );
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

    public void revise(
            String title,
            String description,
            int estimatedDurationHours,
            CourseLevel level,
            Money price,
            int maximumSeats,
            UUID categoryId,
            UUID instructorId
    ) {
        if (status != CourseStatus.DRAFT && status != CourseStatus.PUBLISHED) {
            throw new IllegalStateException("Archived courses cannot be revised");
        }
        validateCourseDetails(title, estimatedDurationHours, level, price, maximumSeats, categoryId, instructorId);
        if (maximumSeats < occupiedSeats) {
            throw new IllegalArgumentException("maximum seats cannot be less than occupied seats");
        }

        this.title = title;
        this.description = description;
        this.estimatedDurationHours = estimatedDurationHours;
        this.level = level;
        this.price = price;
        this.maximumSeats = maximumSeats;
        this.categoryId = categoryId;
        this.instructorId = instructorId;
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

    private static void validateCourseDetails(
            String title,
            int estimatedDurationHours,
            CourseLevel level,
            Money price,
            int maximumSeats,
            UUID categoryId,
            UUID instructorId
    ) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (estimatedDurationHours <= 0) {
            throw new IllegalArgumentException("estimated duration hours must be positive");
        }
        Objects.requireNonNull(level, "level must not be null");
        Objects.requireNonNull(price, "price must not be null");
        if (maximumSeats <= 0) {
            throw new IllegalArgumentException("maximum seats must be positive");
        }
        Objects.requireNonNull(categoryId, "category id must not be null");
        Objects.requireNonNull(instructorId, "instructor id must not be null");
    }

    private static void validateOccupancy(int occupiedSeats, int maximumSeats, CourseStatus status) {
        Objects.requireNonNull(status, "status must not be null");
        if (occupiedSeats < 0 || occupiedSeats > maximumSeats) {
            throw new IllegalArgumentException("occupied seats must be between zero and maximum seats");
        }
        if (status == CourseStatus.DRAFT && occupiedSeats != 0) {
            throw new IllegalArgumentException("draft courses cannot have occupied seats");
        }
    }
}
