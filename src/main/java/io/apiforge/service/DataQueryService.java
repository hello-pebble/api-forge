package io.apiforge.service;

import io.apiforge.domain.Dataset;
import io.apiforge.domain.DatasetStatus;
import io.apiforge.query.DynamicQueryBuilder;
import io.apiforge.query.QueryResult;
import io.apiforge.repository.DatasetRepository;
import io.apiforge.web.error.DatasetNotFoundException;
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

    private final DatasetRepository datasetRepository;
    private final DynamicQueryBuilder queryBuilder;
    private final DSLContext dsl;

    public DataQueryService(DatasetRepository datasetRepository, DynamicQueryBuilder queryBuilder, DSLContext dsl) {
        this.datasetRepository = datasetRepository;
        this.queryBuilder = queryBuilder;
        this.dsl = dsl;
    }

    @Transactional(readOnly = true)
    public QueryResult query(String datasetKey, Map<String, String> filterParams, String sortParam, int page, int size) {
        Dataset dataset = datasetRepository.findByDatasetKeyAndStatus(datasetKey, DatasetStatus.PUBLISHED)
                .orElseThrow(() -> new DatasetNotFoundException(datasetKey));

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

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
                .offset(safePage * safeSize)
                .fetchMaps();

        return new QueryResult(dataset, safePage, safeSize, total == null ? 0 : total, rows);
    }
}
