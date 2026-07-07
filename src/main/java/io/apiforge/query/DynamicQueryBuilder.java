package io.apiforge.query;

import io.apiforge.domain.Dataset;
import io.apiforge.domain.DatasetColumn;
import io.apiforge.domain.FilterType;
import io.apiforge.web.error.InvalidQueryException;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.SortField;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 데이터셋 메타데이터로부터 jOOQ 쿼리 조각을 조립하는 엔진.
 *
 * 설계 원칙 (레거시 문자열 SQL 조립의 재설계):
 * - 식별자(테이블·칼럼명)는 관리자가 등록한 메타데이터에 있는 것만 사용 — 화이트리스트
 * - 요청 값은 전부 jOOQ 바인드 파라미터로 처리 — SQL Injection 원천 차단
 * - 등록되지 않은 칼럼, 허용되지 않은 필터·정렬 요청은 400으로 즉시 거부
 */
@Component
public class DynamicQueryBuilder {

    /** 소스 테이블/칼럼 식별자 허용 패턴 — 등록 시점에도 동일 규칙으로 검증 */
    public static final String IDENTIFIER_PATTERN = "^[A-Za-z][A-Za-z0-9_]*$";

    /** SELECT 절 — 노출 칼럼을 등록 순서대로 */
    public List<Field<?>> selectFields(Dataset dataset) {
        List<Field<?>> fields = new ArrayList<>();
        for (DatasetColumn col : dataset.getColumns()) {
            fields.add(DSL.field(DSL.name(col.getSourceColumn())));
        }
        return fields;
    }

    /** WHERE 절 — 필터 타입별 Condition 생성. 값은 전부 바인드 파라미터. */
    public List<Condition> conditions(Dataset dataset, Map<String, String> filterParams) {
        List<Condition> conditions = new ArrayList<>();
        for (Map.Entry<String, String> entry : filterParams.entrySet()) {
            String paramName = entry.getKey();
            String value = entry.getValue();
            if (value == null || value.isBlank()) {
                continue;
            }

            DatasetColumn col = dataset.findColumn(paramName)
                    .orElseThrow(() -> new InvalidQueryException("지원하지 않는 필터 파라미터입니다: " + paramName));
            if (col.getFilterType() == FilterType.NONE) {
                throw new InvalidQueryException("필터가 허용되지 않은 칼럼입니다: " + paramName);
            }

            Field<Object> field = DSL.field(DSL.name(col.getSourceColumn()));
            conditions.add(toCondition(field, col, value));
        }
        return conditions;
    }

    private Condition toCondition(Field<Object> field, DatasetColumn col, String value) {
        return switch (col.getFilterType()) {
            case EQUALS -> field.eq(value);
            case WORDS -> field.containsIgnoreCase(value);
            case CHECK -> field.in(Arrays.stream(value.split(","))
                    .map(String::trim)
                    .filter(v -> !v.isEmpty())
                    .toList());
            case DATE -> dateCondition(field, col, value);
            case NONE -> throw new InvalidQueryException("필터가 허용되지 않은 칼럼입니다: " + col.getSourceColumn());
        };
    }

    /** DATE 필터 — "from,to"는 BETWEEN, 단일 값은 해당 일자 일치 */
    private Condition dateCondition(Field<Object> field, DatasetColumn col, String value) {
        String[] range = value.split(",", -1);
        try {
            Field<LocalDate> dateField = field.coerce(LocalDate.class);
            if (range.length == 1) {
                return dateField.eq(LocalDate.parse(range[0].trim()));
            }
            if (range.length == 2) {
                return dateField.between(LocalDate.parse(range[0].trim()), LocalDate.parse(range[1].trim()));
            }
        } catch (DateTimeParseException e) {
            // fall through
        }
        throw new InvalidQueryException(
                "날짜 필터 형식이 잘못되었습니다 (yyyy-MM-dd 또는 yyyy-MM-dd,yyyy-MM-dd): " + col.getSourceColumn());
    }

    /** ORDER BY 절 — "COL" 또는 "COL,desc" 형식. sortable 칼럼만 허용. */
    public List<SortField<?>> orderBy(Dataset dataset, String sortParam) {
        if (sortParam == null || sortParam.isBlank()) {
            return List.of();
        }
        String[] parts = sortParam.split(",");
        String columnName = parts[0].trim();
        boolean desc = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim());

        DatasetColumn col = dataset.findColumn(columnName)
                .orElseThrow(() -> new InvalidQueryException("지원하지 않는 정렬 칼럼입니다: " + columnName));
        if (!col.isSortable()) {
            throw new InvalidQueryException("정렬이 허용되지 않은 칼럼입니다: " + columnName);
        }

        Field<Object> field = DSL.field(DSL.name(col.getSourceColumn()));
        return List.of(desc ? field.desc() : field.asc());
    }
}
