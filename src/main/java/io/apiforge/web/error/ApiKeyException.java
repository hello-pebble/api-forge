package io.apiforge.web.error;

/** API 키 누락·오류·폐기 → 401 */
public class ApiKeyException extends RuntimeException {

    public ApiKeyException(String message) {
        super(message);
    }
}
