package io.apiforge.repository;

import io.apiforge.domain.Dataset;
import io.apiforge.domain.DatasetStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DatasetRepository extends JpaRepository<Dataset, Long> {

    Optional<Dataset> findByDatasetKey(String datasetKey);

    Optional<Dataset> findByDatasetKeyAndStatus(String datasetKey, DatasetStatus status);

    List<Dataset> findAllByStatus(DatasetStatus status);

    boolean existsByDatasetKey(String datasetKey);

    /**
     * 캐시 적재용 — columns 를 함께 로드해 영속성 컨텍스트 밖(준영속)에서도 안전하게 읽히도록 한다.
     * LAZY 상태로 캐싱하면 컨텍스트 종료 후 LazyInitializationException 이 난다.
     */
    @EntityGraph(attributePaths = "columns")
    Optional<Dataset> findWithColumnsByDatasetKeyAndStatus(String datasetKey, DatasetStatus status);

    @EntityGraph(attributePaths = "columns")
    List<Dataset> findWithColumnsByStatus(DatasetStatus status);
}
