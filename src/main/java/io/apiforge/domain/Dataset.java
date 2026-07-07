package io.apiforge.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 관리자가 등록하는 데이터셋 메타데이터.
 * 소스 테이블과 노출 칼럼 설정만으로 Open API 엔드포인트가 정의된다.
 */
@Entity
@Table(name = "DATASET")
public class Dataset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** API 경로에 쓰이는 키 (/api/v1/datasets/{datasetKey}) */
    @Column(nullable = false, unique = true, length = 50)
    private String datasetKey;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 1000)
    private String description;

    /** 데이터를 읽어올 소스 테이블 (등록 시 식별자 규칙 검증) */
    @Column(nullable = false, length = 100)
    private String sourceTable;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DatasetStatus status = DatasetStatus.DRAFT;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime publishedAt;

    @OneToMany(mappedBy = "dataset", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<DatasetColumn> columns = new ArrayList<>();

    protected Dataset() {
    }

    public Dataset(String datasetKey, String name, String description, String sourceTable) {
        this.datasetKey = datasetKey;
        this.name = name;
        this.description = description;
        this.sourceTable = sourceTable;
    }

    public void addColumn(DatasetColumn column) {
        column.assignTo(this, columns.size());
        columns.add(column);
    }

    public void publish() {
        this.status = DatasetStatus.PUBLISHED;
        this.publishedAt = LocalDateTime.now();
    }

    public Optional<DatasetColumn> findColumn(String sourceColumn) {
        return columns.stream()
                .filter(c -> c.getSourceColumn().equalsIgnoreCase(sourceColumn))
                .findFirst();
    }

    public Long getId() {
        return id;
    }

    public String getDatasetKey() {
        return datasetKey;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getSourceTable() {
        return sourceTable;
    }

    public DatasetStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public List<DatasetColumn> getColumns() {
        return columns;
    }
}
