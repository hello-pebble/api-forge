package io.apiforge.web.error;

/** 잘못된 필터/정렬/포맷 요청 → 400 */
public class InvalidQueryException extends RuntimeException {

    public InvalidQueryException(String message) {
        super(message);
    }
}
