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
import java.util.concurrent.atomic.AtomicLong;
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
 *
 * 저장 대상은 "발행된 데이터셋이 실제로 존재하는 경우"뿐이다. 없는 키까지 캐싱하면
 * 임의의 키를 반복 호출하는 것만으로 맵이 무한히 커져 메모리를 고갈시킬 수 있다.
 * 따라서 캐시 크기는 발행된 데이터셋 수(관리자가 통제하는 값)로 제한된다.
 */
@Component
public class DatasetMetadataCache {

    private final DatasetRepository datasetRepository;

    /** datasetKey → 발행 데이터셋. 존재하는 것만 담는다(부재는 캐싱하지 않는다). */
    private final ConcurrentMap<String, Dataset> publishedByKey = new ConcurrentHashMap<>();

    /** 발행 데이터셋 카탈로그 전체 */
    private final AtomicReference<List<Dataset>> catalog = new AtomicReference<>();

    /**
     * 무효화 세대. DB 를 읽기 직전 값과 저장 직전 값이 다르면 그 사이에 무효화가
     * 일어난 것이므로 읽어온 값을 버린다.
     *
     * 이 가드가 없으면 다음 순서로 낡은 값이 캐시에 영구히 남는다.
     *   1) 조회 스레드가 DB 를 읽는다 (아직 변경 전 상태)
     *   2) 관리 작업이 커밋되고 캐시를 비운다 — 아직 없는 키라 지울 것이 없다
     *   3) 조회 스레드가 1)에서 읽은 낡은 값을 저장한다
     */
    private final AtomicLong generation = new AtomicLong();

    public DatasetMetadataCache(DatasetRepository datasetRepository) {
        this.datasetRepository = datasetRepository;
    }

    @Transactional(readOnly = true)
    public Optional<Dataset> findPublished(String datasetKey) {
        Dataset cached = publishedByKey.get(datasetKey);
        if (cached != null) {
            return Optional.of(cached);
        }
        long loadedAt = generation.get();
        Optional<Dataset> loaded =
                datasetRepository.findWithColumnsByDatasetKeyAndStatus(datasetKey, DatasetStatus.PUBLISHED);
        if (loaded.isPresent() && generation.get() == loadedAt) {
            publishedByKey.put(datasetKey, loaded.get());
        }
        return loaded;
    }

    @Transactional(readOnly = true)
    public List<Dataset> publishedCatalog() {
        List<Dataset> cached = catalog.get();
        if (cached != null) {
            return cached;
        }
        long loadedAt = generation.get();
        List<Dataset> loaded = List.copyOf(datasetRepository.findWithColumnsByStatus(DatasetStatus.PUBLISHED));
        if (generation.get() == loadedAt) {
            catalog.set(loaded);
        }
        return loaded;
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

    /**
     * 세대를 먼저 올린 뒤 비운다. 순서가 반대면, 비우기와 세대 증가 사이에 값을 저장한
     * 스레드의 항목이 살아남는다.
     */
    private void clear() {
        generation.incrementAndGet();
        publishedByKey.clear();
        catalog.set(null);
    }
}
