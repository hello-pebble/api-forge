package io.apiforge.repository;

import io.apiforge.domain.ApiKeyUsageDaily;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ApiKeyUsageDailyRepository extends JpaRepository<ApiKeyUsageDaily, Long> {

    /** 해당 (키, 데이터셋, 일자) 행이 있으면 카운트 증가. 반환값 0이면 행 없음. */
    @Modifying
    @Query("UPDATE ApiKeyUsageDaily u SET u.requestCount = u.requestCount + 1 "
            + "WHERE u.apiKey.id = :apiKeyId AND u.datasetKey = :datasetKey AND u.usageDate = :date")
    int increment(@Param("apiKeyId") Long apiKeyId,
                  @Param("datasetKey") String datasetKey,
                  @Param("date") LocalDate date);

    List<ApiKeyUsageDaily> findByApiKey_KeyPrefixOrderByUsageDateDescDatasetKeyAsc(String keyPrefix);
}
