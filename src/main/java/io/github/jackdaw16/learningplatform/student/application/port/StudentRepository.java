package io.github.jackdaw16.learningplatform.student.application.port;

import io.github.jackdaw16.learningplatform.student.domain.Student;
import java.util.Optional;
import java.util.UUID;

public interface StudentRepository {

    void save(Student student);

    Optional<Student> findById(UUID id);
}
