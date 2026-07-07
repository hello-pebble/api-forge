package io.apiforge.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 데이터셋의 노출 칼럼 설정.
 * 소스 칼럼 ↔ 표시명 매핑과 필터/정렬 허용 여부를 정의한다.
 * 동적 쿼리는 여기 등록된 칼럼만 참조할 수 있다(화이트리스트).
 */
@Entity
@Table(name = "DATASET_COLUMN")
public class DatasetColumn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dataset_id")
    private Dataset dataset;

    /** 소스 테이블의 실제 칼럼명 */
    @Column(nullable = false, length = 100)
    private String sourceColumn;

    /** 사용자에게 보여줄 표시명 (CSV 헤더 등) */
    @Column(nullable = false, length = 200)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FilterType filterType = FilterType.NONE;

    /** 정렬 파라미터 허용 여부 */
    @Column(nullable = false)
    private boolean sortable;

    /** 노출 순서 */
    @Column(nullable = false)
    private int position;

    protected DatasetColumn() {
    }

    public DatasetColumn(String sourceColumn, String displayName, FilterType filterType, boolean sortable) {
        this.sourceColumn = sourceColumn;
        this.displayName = displayName;
        this.filterType = filterType;
        this.sortable = sortable;
    }

    void assignTo(Dataset dataset, int position) {
        this.dataset = dataset;
        this.position = position;
    }

    public Long getId() {
        return id;
    }

    public String getSourceColumn() {
        return sourceColumn;
    }

    public String getDisplayName() {
        return displayName;
    }

    public FilterType getFilterType() {
        return filterType;
    }

    public boolean isSortable() {
        return sortable;
    }

    public int getPosition() {
        return position;
    }
}
