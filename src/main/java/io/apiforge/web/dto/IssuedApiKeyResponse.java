package io.apiforge.web.dto;

/**
 * 키 발급 응답. rawKey는 이 응답에서만 확인 가능하며 이후 다시 조회할 수 없다.
 */
public record IssuedApiKeyResponse(
        String rawKey,
        String keyPrefix,
        String label,
        String notice) {

    public static IssuedApiKeyResponse of(String rawKey, String keyPrefix, String label) {
        return new IssuedApiKeyResponse(rawKey, keyPrefix, label,
                "이 키는 지금만 확인할 수 있습니다. 안전한 곳에 보관하세요.");
    }
}
