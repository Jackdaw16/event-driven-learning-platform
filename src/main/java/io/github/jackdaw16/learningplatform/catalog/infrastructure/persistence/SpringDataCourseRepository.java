package io.github.jackdaw16.learningplatform.catalog.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface SpringDataCourseRepository extends JpaRepository<CourseJpaEntity, UUID>, JpaSpecificationExecutor<CourseJpaEntity> {

    boolean existsByCategoryId(UUID categoryId);

    boolean existsByInstructorId(UUID instructorId);
}
