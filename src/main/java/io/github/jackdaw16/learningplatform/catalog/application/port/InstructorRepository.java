package io.github.jackdaw16.learningplatform.catalog.application.port;

import io.github.jackdaw16.learningplatform.catalog.domain.Instructor;
import io.github.jackdaw16.learningplatform.catalog.application.PageQuery;
import io.github.jackdaw16.learningplatform.catalog.application.PageResult;
import java.util.Optional;
import java.util.UUID;

public interface InstructorRepository {

    void save(Instructor instructor);

    Optional<Instructor> findById(UUID id);

    Optional<Instructor> findByEmail(String email);

    PageResult<Instructor> list(PageQuery pageQuery);

    void deleteById(UUID id);
}
