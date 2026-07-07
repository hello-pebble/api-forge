package io.apiforge.query;

import io.apiforge.domain.Dataset;
import io.apiforge.domain.DatasetColumn;
import io.apiforge.domain.FilterType;
import io.apiforge.web.error.InvalidQueryException;
import org.jooq.Condition;
import org.jooq.SortField;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DynamicQueryBuilderTest {

    private final DynamicQueryBuilder builder = new DynamicQueryBuilder();

    private Dataset dataset;

    @BeforeEach
    void setUp() {
        dataset = new Dataset("bills", "국회 의안 정보", "테스트", "NA_BILL");
        dataset.addColumn(new DatasetColumn("BILL_ID", "의안번호", FilterType.EQUALS, true));
        dataset.addColumn(new DatasetColumn("BILL_NM", "의안명", FilterType.WORDS, false));
        dataset.addColumn(new DatasetColumn("COMMITTEE", "소관위원회", FilterType.CHECK, true));
        dataset.addColumn(new DatasetColumn("PROPOSE_DT", "발의일자", FilterType.DATE, true));
        dataset.addColumn(new DatasetColumn("SECRET_COL", "내부칼럼", FilterType.NONE, false));
    }

    @Test
    @DisplayName("EQUALS 필터는 = 조건을 생성하고 값을 바인드한다")
    void equalsFilter() {
        List<Condition> conditions = builder.conditions(dataset, Map.of("BILL_ID", "2200001"));

        assertThat(conditions).hasSize(1);
        assertThat(conditions.get(0).toString()).contains("BILL_ID").contains("2200001");
    }

    @Test
    @DisplayName("WORDS 필터는 대소문자 무시 LIKE 조건을 생성한다")
    void wordsFilter() {
        List<Condition> conditions = builder.conditions(dataset, Map.of("BILL_NM", "데이터"));

        assertThat(conditions.get(0).toString().toLowerCase()).contains("like");
    }

    @Test
    @DisplayName("CHECK 필터는 콤마 구분 값을 IN 조건으로 생성한다")
    void checkFilter() {
        List<Condition> conditions = builder.conditions(dataset, Map.of("COMMITTEE", "행정안전위원회, 정무위원회"));

        assertThat(conditions.get(0).toString().toLowerCase()).contains("in (");
        assertThat(conditions.get(0).toString()).contains("행정안전위원회").contains("정무위원회");
    }

    @Test
    @DisplayName("DATE 필터는 from,to 값을 BETWEEN 조건으로 생성한다")
    void dateRangeFilter() {
        List<Condition> conditions = builder.conditions(dataset, Map.of("PROPOSE_DT", "2026-01-01,2026-06-30"));

        assertThat(conditions.get(0).toString().toLowerCase()).contains("between");
    }

    @Test
    @DisplayName("잘못된 날짜 형식은 400 예외를 던진다")
    void invalidDateFormat() {
        assertThatThrownBy(() -> builder.conditions(dataset, Map.of("PROPOSE_DT", "2026/01/01")))
                .isInstanceOf(InvalidQueryException.class);
    }

    @Test
    @DisplayName("등록되지 않은 칼럼으로 필터하면 거부한다 — 화이트리스트")
    void unregisteredColumnRejected() {
        assertThatThrownBy(() -> builder.conditions(dataset, Map.of("EVIL_COL", "x")))
                .isInstanceOf(InvalidQueryException.class)
                .hasMessageContaining("EVIL_COL");
    }

    @Test
    @DisplayName("SQL 조각을 파라미터명으로 위장한 요청은 칼럼 조회 단계에서 거부된다")
    void injectionViaParamNameRejected() {
        assertThatThrownBy(() -> builder.conditions(dataset, Map.of("BILL_ID; DROP TABLE NA_BILL--", "x")))
                .isInstanceOf(InvalidQueryException.class);
    }

    @Test
    @DisplayName("필터 값의 SQL 조각은 바인드 파라미터로 문자열 처리된다")
    void injectionViaValueIsBound() {
        List<Condition> conditions = builder.conditions(dataset, Map.of("BILL_ID", "' OR '1'='1"));

        // jOOQ 인라인 렌더링 시 작은따옴표가 이스케이프('')되어 문자열 리터럴로만 남는다
        assertThat(conditions.get(0).toString()).contains("''");
    }

    @Test
    @DisplayName("filterType=NONE 칼럼은 필터를 허용하지 않는다")
    void noneFilterRejected() {
        assertThatThrownBy(() -> builder.conditions(dataset, Map.of("SECRET_COL", "x")))
                .isInstanceOf(InvalidQueryException.class);
    }

    @Test
    @DisplayName("정렬은 sortable 칼럼만 허용하고 방향을 반영한다")
    void sortableColumn() {
        List<SortField<?>> order = builder.orderBy(dataset, "PROPOSE_DT,desc");

        assertThat(order).hasSize(1);
        assertThat(order.get(0).toString().toLowerCase()).contains("desc");
    }

    @Test
    @DisplayName("sortable=false 칼럼 정렬 요청은 거부한다")
    void nonSortableRejected() {
        assertThatThrownBy(() -> builder.orderBy(dataset, "BILL_NM"))
                .isInstanceOf(InvalidQueryException.class);
    }

    @Test
    @DisplayName("SELECT 필드는 등록 순서를 유지한다")
    void selectFieldsInOrder() {
        assertThat(builder.selectFields(dataset)).hasSize(5);
        assertThat(builder.selectFields(dataset).get(0).getName()).isEqualTo("BILL_ID");
    }
}
