package io.apiforge.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * API 키 생성·해싱 유틸.
 *
 * 원문 키는 발급 시 한 번만 노출하고 DB에는 SHA-256 해시만 저장한다.
 * (키는 192비트 난수라 엔트로피가 충분하므로 bcrypt 대신 SHA-256으로 충분하며,
 *  조회는 앞 12자 prefix 인덱스로 O(1) 처리한다.)
 */
public final class ApiKeys {

    public static final int PREFIX_LENGTH = 12;

    private static final SecureRandom RANDOM = new SecureRandom();

    private ApiKeys() {
    }

    /** 48 hex 문자(24바이트 난수)로 된 원문 키 생성 */
    public static String generateRawKey() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    public static String prefixOf(String rawKey) {
        return rawKey.substring(0, PREFIX_LENGTH);
    }

    public static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 미지원 환경", e);
        }
    }
}
