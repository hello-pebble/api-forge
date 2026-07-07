package io.apiforge.domain;

public enum ApiKeyStatus {
    /** 사용 가능 */
    ACTIVE,
    /** 폐기됨 — 인증 거부 */
    REVOKED
}
