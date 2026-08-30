package io.github.jackdaw16.learningplatform.catalog.infrastructure.persistence;

import io.github.jackdaw16.learningplatform.catalog.application.port.CourseSeatInventory;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(propagation = Propagation.MANDATORY)
public class PostgresCourseSeatInventory implements CourseSeatInventory {

    private final JdbcTemplate jdbcTemplate;

    public PostgresCourseSeatInventory(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean reserve(UUID courseId) {
        return jdbcTemplate.update(
                "UPDATE courses SET occupied_seats = occupied_seats + 1 "
                        + "WHERE id = ? AND status = 'PUBLISHED' AND occupied_seats < maximum_seats",
                courseId
        ) == 1;
    }

    @Override
    public boolean release(UUID courseId) {
        return jdbcTemplate.update(
                "UPDATE courses SET occupied_seats = occupied_seats - 1 "
                        + "WHERE id = ? AND occupied_seats > 0",
                courseId
        ) == 1;
    }
}
