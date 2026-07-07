package io.apiforge.web.dto;

import io.apiforge.domain.Dataset;
import io.apiforge.domain.DatasetColumn;
import io.apiforge.domain.DatasetStatus;
import io.apiforge.domain.FilterType;

import java.time.LocalDateTime;
import java.util.List;

public record DatasetView(
        String datasetKey,
        String name,
        String description,
        String sourceTable,
        DatasetStatus status,
        LocalDateTime createdAt,
        LocalDateTime publishedAt,
        List<ColumnView> columns) {

    public record ColumnView(String sourceColumn, String displayName, FilterType filterType, boolean sortable) {

        static ColumnView from(DatasetColumn col) {
            return new ColumnView(col.getSourceColumn(), col.getDisplayName(), col.getFilterType(), col.isSortable());
        }
    }

    public static DatasetView from(Dataset dataset) {
        return new DatasetView(
                dataset.getDatasetKey(),
                dataset.getName(),
                dataset.getDescription(),
                dataset.getSourceTable(),
                dataset.getStatus(),
                dataset.getCreatedAt(),
                dataset.getPublishedAt(),
                dataset.getColumns().stream().map(ColumnView::from).toList());
    }
}
