package io.github.jackdaw16.learningplatform.catalog.infrastructure.http;

import io.github.jackdaw16.learningplatform.catalog.domain.Instructor;
import java.util.UUID;

public record InstructorResponse(UUID id, String name, String email, String biography) {

    public static InstructorResponse from(Instructor instructor) {
        return new InstructorResponse(instructor.id(), instructor.name(), instructor.email(), instructor.biography());
    }
}
