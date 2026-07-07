package io.apiforge.repository;

import io.apiforge.domain.Dataset;
import io.apiforge.domain.DatasetStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DatasetRepository extends JpaRepository<Dataset, Long> {

    Optional<Dataset> findByDatasetKey(String datasetKey);

    Optional<Dataset> findByDatasetKeyAndStatus(String datasetKey, DatasetStatus status);

    List<Dataset> findAllByStatus(DatasetStatus status);

    boolean existsByDatasetKey(String datasetKey);
}
