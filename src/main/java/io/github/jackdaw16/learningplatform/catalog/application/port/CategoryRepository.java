package io.github.jackdaw16.learningplatform.catalog.application.port;

import io.github.jackdaw16.learningplatform.catalog.domain.Category;
import io.github.jackdaw16.learningplatform.catalog.application.PageQuery;
import io.github.jackdaw16.learningplatform.catalog.application.PageResult;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository {

    void save(Category category);

    Optional<Category> findById(UUID id);

    Optional<Category> findByName(String name);

    PageResult<Category> list(PageQuery pageQuery);

    void deleteById(UUID id);
}
