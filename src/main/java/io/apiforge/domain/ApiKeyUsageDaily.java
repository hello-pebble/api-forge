package io.apiforge.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;

/**
 * (API 키, 데이터셋, 일자) 단위 요청 수 집계.
 * 요청 로그를 원본 저장하지 않고 일자별로 누적해 통계 조회 비용을 낮춘다.
 */
@Entity
@Table(name = "API_KEY_USAGE_DAILY",
        uniqueConstraints = @UniqueConstraint(columnNames = {"api_key_id", "dataset_key", "usage_date"}))
public class ApiKeyUsageDaily {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "api_key_id")
    private ApiKey apiKey;

    @Column(name = "dataset_key", nullable = false, length = 50)
    private String datasetKey;

    @Column(name = "usage_date", nullable = false)
    private LocalDate usageDate;

    @Column(nullable = false)
    private long requestCount;

    protected ApiKeyUsageDaily() {
    }

    public ApiKeyUsageDaily(ApiKey apiKey, String datasetKey, LocalDate usageDate, long requestCount) {
        this.apiKey = apiKey;
        this.datasetKey = datasetKey;
        this.usageDate = usageDate;
        this.requestCount = requestCount;
    }

    public String getDatasetKey() {
        return datasetKey;
    }

    public LocalDate getUsageDate() {
        return usageDate;
    }

    public long getRequestCount() {
        return requestCount;
    }
}
