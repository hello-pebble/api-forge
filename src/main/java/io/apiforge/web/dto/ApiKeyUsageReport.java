package io.apiforge.web.dto;

import io.apiforge.domain.ApiKeyUsageDaily;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 키별 사용량 통계 — 총계 + 일자·데이터셋별 내역 */
public record ApiKeyUsageReport(
        String keyPrefix,
        String label,
        long totalRequests,
        LocalDateTime lastUsedAt,
        List<DailyUsage> daily) {

    public record DailyUsage(String datasetKey, LocalDate usageDate, long requestCount) {

        static DailyUsage from(ApiKeyUsageDaily u) {
            return new DailyUsage(u.getDatasetKey(), u.getUsageDate(), u.getRequestCount());
        }
    }

    public static ApiKeyUsageReport of(io.apiforge.domain.ApiKey key, List<ApiKeyUsageDaily> rows) {
        return new ApiKeyUsageReport(
                key.getKeyPrefix(),
                key.getLabel(),
                key.getTotalRequests(),
                key.getLastUsedAt(),
                rows.stream().map(DailyUsage::from).toList());
    }
}
