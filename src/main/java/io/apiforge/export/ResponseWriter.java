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

    /**
     * 브라우저에서 바로 열기보다 파일로 저장되는 게 자연스러운 포맷이면 확장자를 반환한다.
     * null 이면 Content-Disposition 없이 인라인으로 응답한다.
     */
    default String downloadExtension() {
        return null;
    }

    void write(QueryResult result, OutputStream out) throws IOException;
}
