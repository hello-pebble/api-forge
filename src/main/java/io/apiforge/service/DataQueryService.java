package io.apiforge.service;

import io.apiforge.domain.Dataset;
import io.apiforge.query.DynamicQueryBuilder;
import io.apiforge.query.QueryResult;
import io.apiforge.web.error.DatasetNotFoundException;
import io.apiforge.web.error.InvalidQueryException;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.SortField;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 발행된 데이터셋에 대한 동적 조회 실행.
 * 메타데이터 로드 → 쿼리 조립 → 카운트/본문 실행 → 공통 결과셋 반환.
 */
@Service
public class DataQueryService {

    public static final int MAX_PAGE_SIZE = 100;
    public static final int DEFAULT_PAGE_SIZE = 20;

    private final DatasetMetadataCache metadataCache;
    private final DynamicQueryBuilder queryBuilder;
    private final DSLContext dsl;

    public DataQueryService(DatasetMetadataCache metadataCache, DynamicQueryBuilder queryBuilder, DSLContext dsl) {
        this.metadataCache = metadataCache;
        this.queryBuilder = queryBuilder;
        this.dsl = dsl;
    }

    @Transactional(readOnly = true)
    public QueryResult query(String datasetKey, Map<String, String> filterParams, String sortParam, int page, int size) {
        Dataset dataset = metadataCache.findPublished(datasetKey)
                .orElseThrow(() -> new DatasetNotFoundException(datasetKey));

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int offset = offsetOf(safePage, safeSize);

        Table<?> table = DSL.table(DSL.name(dataset.getSourceTable()));
        List<Field<?>> fields = queryBuilder.selectFields(dataset);
        List<Condition> conditions = queryBuilder.conditions(dataset, filterParams);
        List<SortField<?>> orderBy = queryBuilder.orderBy(dataset, sortParam);

        Long total = dsl.selectCount()
                .from(table)
                .where(conditions)
                .fetchOne(0, Long.class);

        List<Map<String, Object>> rows = dsl.select(fields)
                .from(table)
                .where(conditions)
                .orderBy(orderBy)
                .limit(safeSize)
                .offset(offset)
                .fetchMaps();

        return new QueryResult(dataset, safePage, safeSize, total == null ? 0 : total, rows);
    }

    /**
     * OFFSET 계산 — page * size 를 int 로 곱하면 오버플로가 나 음수 OFFSET 이 되고
     * SQL 실행 단계에서 500 으로 터진다. long 으로 계산해 범위를 벗어나면 400 으로 거부한다.
     */
    private static int offsetOf(int page, int size) {
        long offset = (long) page * size;
        if (offset > Integer.MAX_VALUE) {
            throw new InvalidQueryException(
                    "페이지 범위를 벗어났습니다 (page * size 는 " + Integer.MAX_VALUE + " 이하여야 합니다): page=" + page);
        }
        return (int) offset;
    }
}
