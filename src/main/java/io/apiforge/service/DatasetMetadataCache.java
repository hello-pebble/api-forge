package io.apiforge.service;

import io.apiforge.domain.Dataset;
import io.apiforge.domain.DatasetStatus;
import io.apiforge.repository.DatasetRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 발행된 데이터셋 메타데이터 캐시.
 *
 * 메타데이터는 관리자가 등록·발행·삭제할 때만 바뀌는데, 조회 요청은 매번 이를 읽는다.
 * (요청당 dataset + dataset_column 2회 SELECT) 이를 메모리에 들고 관리 작업 시점에만
 * 무효화해 조회 경로의 JPA 접근을 제거한다.
 *
 * 캐시에 담기는 Dataset 은 준영속(detached) 인스턴스다. columns 를 EntityGraph 로
 * 미리 초기화해 두므로 읽기 전용 게터 접근은 안전하며, 이 인스턴스를 수정하지 않는다.
 */
@Component
public class DatasetMetadataCache {

    private final DatasetRepository datasetRepository;

    /** datasetKey → 발행 데이터셋. 미발행/부재도 Optional.empty() 로 캐싱해 반복 조회를 막는다. */
    private final ConcurrentMap<String, Optional<Dataset>> publishedByKey = new ConcurrentHashMap<>();

    /** 발행 데이터셋 카탈로그 전체 */
    private final AtomicReference<List<Dataset>> catalog = new AtomicReference<>();

    public DatasetMetadataCache(DatasetRepository datasetRepository) {
        this.datasetRepository = datasetRepository;
    }

    @Transactional(readOnly = true)
    public Optional<Dataset> findPublished(String datasetKey) {
        return publishedByKey.computeIfAbsent(datasetKey,
                key -> datasetRepository.findWithColumnsByDatasetKeyAndStatus(key, DatasetStatus.PUBLISHED));
    }

    @Transactional(readOnly = true)
    public List<Dataset> publishedCatalog() {
        List<Dataset> cached = catalog.get();
        if (cached == null) {
            cached = List.copyOf(datasetRepository.findWithColumnsByStatus(DatasetStatus.PUBLISHED));
            catalog.set(cached);
        }
        return cached;
    }

    /**
     * 메타데이터 변경 시 무효화. 트랜잭션 안에서 호출되면 커밋 이후에 비운다 —
     * 커밋 전에 비우면 롤백될 변경이나 진행 중인 다른 읽기가 캐시를 다시 오염시킬 수 있다.
     */
    public void invalidate() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    clear();
                }
            });
        } else {
            clear();
        }
    }

    private void clear() {
        publishedByKey.clear();
        catalog.set(null);
    }
}
