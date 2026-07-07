package io.apiforge.service;

import io.apiforge.domain.Dataset;
import io.apiforge.domain.DatasetColumn;
import io.apiforge.query.DynamicQueryBuilder;
import io.apiforge.repository.DatasetRepository;
import io.apiforge.web.dto.DatasetCreateRequest;
import io.apiforge.web.error.DatasetNotFoundException;
import io.apiforge.web.error.InvalidQueryException;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 데이터셋 메타데이터 등록·발행 관리.
 * 발행 시점에 소스 테이블·칼럼이 실제 DB에 존재하는지 검증한다.
 */
@Service
public class DatasetAdminService {

    private final DatasetRepository datasetRepository;
    private final DSLContext dsl;

    public DatasetAdminService(DatasetRepository datasetRepository, DSLContext dsl) {
        this.datasetRepository = datasetRepository;
        this.dsl = dsl;
    }

    @Transactional
    public Dataset create(DatasetCreateRequest request) {
        if (datasetRepository.existsByDatasetKey(request.datasetKey())) {
            throw new InvalidQueryException("이미 존재하는 데이터셋 키입니다: " + request.datasetKey());
        }
        validateIdentifier(request.sourceTable(), "소스 테이블");

        Dataset dataset = new Dataset(
                request.datasetKey(), request.name(), request.description(), request.sourceTable());
        request.columns().forEach(col -> {
            validateIdentifier(col.sourceColumn(), "소스 칼럼");
            dataset.addColumn(new DatasetColumn(
                    col.sourceColumn(), col.displayName(), col.filterType(), col.sortable()));
        });
        return datasetRepository.save(dataset);
    }

    @Transactional
    public Dataset publish(String datasetKey) {
        Dataset dataset = datasetRepository.findByDatasetKey(datasetKey)
                .orElseThrow(() -> new DatasetNotFoundException(datasetKey));
        verifySourceExists(dataset);
        dataset.publish();
        return dataset;
    }

    @Transactional
    public void delete(String datasetKey) {
        Dataset dataset = datasetRepository.findByDatasetKey(datasetKey)
                .orElseThrow(() -> new DatasetNotFoundException(datasetKey));
        datasetRepository.delete(dataset);
    }

    /** 발행 전 소스 테이블·칼럼 실존 검증 — LIMIT 0 프로브 쿼리 */
    private void verifySourceExists(Dataset dataset) {
        List<Field<?>> fields = dataset.getColumns().stream()
                .<Field<?>>map(c -> DSL.field(DSL.name(c.getSourceColumn())))
                .toList();
        try {
            dsl.select(fields)
                    .from(DSL.table(DSL.name(dataset.getSourceTable())))
                    .limit(0)
                    .fetch();
        } catch (Exception e) {
            throw new InvalidQueryException(
                    "소스 테이블 또는 칼럼이 존재하지 않아 발행할 수 없습니다: " + dataset.getSourceTable());
        }
    }

    private void validateIdentifier(String identifier, String label) {
        if (identifier == null || !identifier.matches(DynamicQueryBuilder.IDENTIFIER_PATTERN)) {
            throw new InvalidQueryException(label + " 이름이 식별자 규칙에 맞지 않습니다: " + identifier);
        }
    }
}
