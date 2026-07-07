package io.apiforge.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 발급된 API 키. 원문은 저장하지 않고 prefix(식별용)와 해시만 보관한다.
 */
@Entity
@Table(name = "API_KEY")
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 원문 키 앞 12자 — 조회·표시용 식별자 */
    @Column(nullable = false, unique = true, length = 12)
    private String keyPrefix;

    /** 원문 키의 SHA-256 (hex) */
    @Column(nullable = false, length = 64)
    private String keyHash;

    @Column(nullable = false, length = 100)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApiKeyStatus status = ApiKeyStatus.ACTIVE;

    @Column(nullable = false)
    private long totalRequests = 0;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime lastUsedAt;

    protected ApiKey() {
    }

    public ApiKey(String keyPrefix, String keyHash, String label) {
        this.keyPrefix = keyPrefix;
        this.keyHash = keyHash;
        this.label = label;
    }

    public void revoke() {
        this.status = ApiKeyStatus.REVOKED;
    }

    public boolean isActive() {
        return status == ApiKeyStatus.ACTIVE;
    }

    public Long getId() {
        return id;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public String getKeyHash() {
        return keyHash;
    }

    public String getLabel() {
        return label;
    }

    public ApiKeyStatus getStatus() {
        return status;
    }

    public long getTotalRequests() {
        return totalRequests;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getLastUsedAt() {
        return lastUsedAt;
    }
}
