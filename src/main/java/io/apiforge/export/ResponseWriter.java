package io.apiforge.export;

import io.apiforge.query.QueryResult;

import java.io.IOException;
import java.io.OutputStream;

/**
 * 응답 포맷 전략 인터페이스.
 * 구현체 빈을 추가하면 새 포맷이 자동 등록된다 (레거시 switch 분기의 재설계).
 */
public interface ResponseWriter {

    /** format 파라미터 값 (json, csv, xml, ...) */
    String format();

    String contentType();

    void write(QueryResult result, OutputStream out) throws IOException;
}
