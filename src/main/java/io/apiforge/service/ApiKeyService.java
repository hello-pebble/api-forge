package io.apiforge.service;

import io.apiforge.domain.ApiKey;
import io.apiforge.repository.ApiKeyRepository;
import io.apiforge.repository.ApiKeyUsageDailyRepository;
import io.apiforge.security.ApiKeys;
import io.apiforge.web.dto.ApiKeyUsageReport;
import io.apiforge.web.error.ApiKeyException;
import io.apiforge.web.error.DatasetNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * API 키 발급·인증·폐기와 사용량 집계.
 */
@Service
public class ApiKeyService {

    public record IssuedKey(String rawKey, ApiKey key) {
    }

    private final ApiKeyRepository apiKeyRepository;
    private final ApiKeyUsageDailyRepository usageRepository;
    private final ApiKeyUsageFlusher usageFlusher;

    public ApiKeyService(ApiKeyRepository apiKeyRepository,
                         ApiKeyUsageDailyRepository usageRepository,
                         ApiKeyUsageFlusher usageFlusher) {
        this.apiKeyRepository = apiKeyRepository;
        this.usageRepository = usageRepository;
        this.usageFlusher = usageFlusher;
    }

    /** 새 키 발급 — 원문은 반환값으로만 노출되고 저장되지 않는다. */
    @Transactional
    public IssuedKey issue(String label) {
        String rawKey;
        do {
            rawKey = ApiKeys.generateRawKey();
        } while (apiKeyRepository.existsByKeyPrefix(ApiKeys.prefixOf(rawKey)));

        ApiKey key = new ApiKey(ApiKeys.prefixOf(rawKey), ApiKeys.sha256(rawKey), label);
        return new IssuedKey(rawKey, apiKeyRepository.save(key));
    }

    /** 고정 원문 키를 등록 (데모 시드 전용, 멱등). */
    @Transactional
    public ApiKey importKey(String label, String rawKey) {
        String prefix = ApiKeys.prefixOf(rawKey);
        return apiKeyRepository.findByKeyPrefix(prefix)
                .orElseGet(() -> apiKeyRepository.save(new ApiKey(prefix, ApiKeys.sha256(rawKey), label)));
    }

    /** 원문 키 인증 — 실패 시 ApiKeyException(401). */
    @Transactional(readOnly = true)
    public ApiKey authenticate(String rawKey) {
        if (rawKey == null || rawKey.length() < ApiKeys.PREFIX_LENGTH) {
            throw new ApiKeyException("API 키가 필요합니다 (X-API-Key 헤더)");
        }
        ApiKey key = apiKeyRepository.findByKeyPrefix(ApiKeys.prefixOf(rawKey))
                .orElseThrow(() -> new ApiKeyException("유효하지 않은 API 키입니다"));
        if (!key.isActive()) {
            throw new ApiKeyException("폐기된 API 키입니다");
        }
        if (!constantTimeEquals(key.getKeyHash(), ApiKeys.sha256(rawKey))) {
            throw new ApiKeyException("유효하지 않은 API 키입니다");
        }
        return key;
    }

    @Transactional(readOnly = true)
    public List<ApiKey> list() {
        usageFlusher.flush();
        return apiKeyRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public void revoke(String keyPrefix) {
        ApiKey key = apiKeyRepository.findByKeyPrefix(keyPrefix)
                .orElseThrow(() -> new ApiKeyException("존재하지 않는 키입니다: " + keyPrefix));
        key.revoke();
    }

    /** 버퍼를 먼저 비워 방금 발생한 요청까지 반영된 수치를 보인다. */
    @Transactional(readOnly = true)
    public ApiKeyUsageReport usage(String keyPrefix) {
        usageFlusher.flush();
        ApiKey key = apiKeyRepository.findByKeyPrefix(keyPrefix)
                .orElseThrow(() -> new DatasetNotFoundException(keyPrefix));
        return ApiKeyUsageReport.of(key,
                usageRepository.findByApiKey_KeyPrefixOrderByUsageDateDescDatasetKeyAsc(keyPrefix));
    }

    /** 해시 비교 — 길이가 같을 때 상수 시간 비교로 타이밍 공격 여지를 줄인다. */
    private static boolean constantTimeEquals(String a, String b) {
        return java.security.MessageDigest.isEqual(
                a.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                b.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
