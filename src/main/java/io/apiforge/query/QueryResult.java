package io.apiforge.query;

import io.apiforge.domain.Dataset;

import java.util.List;
import java.util.Map;

/**
 * 동적 쿼리 실행 결과 — 포맷 Writer들이 공통으로 소비하는 중간 표현.
 */
public record QueryResult(
        Dataset dataset,
        int page,
        int size,
        long totalCount,
        List<Map<String, Object>> rows) {
}
