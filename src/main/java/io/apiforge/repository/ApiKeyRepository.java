package io.apiforge.repository;

import io.apiforge.domain.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {

    Optional<ApiKey> findByKeyPrefix(String keyPrefix);

    boolean existsByKeyPrefix(String keyPrefix);

    List<ApiKey> findAllByOrderByCreatedAtDesc();

    /**
     * 누적 호출수 반영 + 마지막 사용 시각 갱신 (단일 UPDATE로 원자 처리).
     * 요청마다 1씩 올리면 인기 키의 동일 행에 락이 직렬화되므로,
     * 버퍼에 모은 delta 를 한 번에 더한다.
     */
    @Modifying
    @Query("UPDATE ApiKey k SET k.totalRequests = k.totalRequests + :delta, k.lastUsedAt = :now WHERE k.id = :id")
    int addRequests(@Param("id") Long id, @Param("delta") long delta, @Param("now") LocalDateTime now);
}
