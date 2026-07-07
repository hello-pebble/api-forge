package io.apiforge.web.dto;

import io.apiforge.domain.ApiKey;
import io.apiforge.domain.ApiKeyStatus;

import java.time.LocalDateTime;

/** 관리자 조회용 — 원문 키·해시는 노출하지 않는다. */
public record ApiKeyView(
        String keyPrefix,
        String label,
        ApiKeyStatus status,
        long totalRequests,
        LocalDateTime createdAt,
        LocalDateTime lastUsedAt) {

    public static ApiKeyView from(ApiKey key) {
        return new ApiKeyView(
                key.getKeyPrefix(),
                key.getLabel(),
                key.getStatus(),
                key.getTotalRequests(),
                key.getCreatedAt(),
                key.getLastUsedAt());
    }
}
