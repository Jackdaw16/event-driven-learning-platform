package io.github.jackdaw16.learningplatform.catalog.application.port;

import io.github.jackdaw16.learningplatform.catalog.domain.Course;
import io.github.jackdaw16.learningplatform.catalog.application.CourseSearchCriteria;
import io.github.jackdaw16.learningplatform.catalog.application.PageQuery;
import io.github.jackdaw16.learningplatform.catalog.application.PageResult;
import java.util.Optional;
import java.util.UUID;

public interface CourseRepository {

    void save(Course course);

    Optional<Course> findById(UUID id);

    PageResult<Course> search(CourseSearchCriteria criteria, PageQuery pageQuery);

    boolean existsByCategoryId(UUID categoryId);

    boolean existsByInstructorId(UUID instructorId);

    void deleteById(UUID id);
}
