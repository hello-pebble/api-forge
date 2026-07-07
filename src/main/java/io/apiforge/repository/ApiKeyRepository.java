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

    /** 총 호출수 증가 + 마지막 사용 시각 갱신 (단일 UPDATE로 원자 처리) */
    @Modifying
    @Query("UPDATE ApiKey k SET k.totalRequests = k.totalRequests + 1, k.lastUsedAt = :now WHERE k.id = :id")
    int touch(@Param("id") Long id, @Param("now") LocalDateTime now);
}
