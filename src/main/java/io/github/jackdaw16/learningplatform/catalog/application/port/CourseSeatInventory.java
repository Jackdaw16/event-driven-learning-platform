package io.github.jackdaw16.learningplatform.catalog.application.port;

import java.util.UUID;

public interface CourseSeatInventory {

    boolean reserve(UUID courseId);

    boolean release(UUID courseId);
}
