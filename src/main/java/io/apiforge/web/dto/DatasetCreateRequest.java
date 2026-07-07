package io.apiforge.web.dto;

import io.apiforge.domain.FilterType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record DatasetCreateRequest(
        @NotBlank @Pattern(regexp = "^[a-z][a-z0-9-]*$", message = "datasetKey는 소문자·숫자·하이픈만 허용됩니다")
        @Size(max = 50)
        String datasetKey,

        @NotBlank @Size(max = 200)
        String name,

        @Size(max = 1000)
        String description,

        @NotBlank @Size(max = 100)
        String sourceTable,

        @NotEmpty @Valid
        List<ColumnRequest> columns) {

    public record ColumnRequest(
            @NotBlank @Size(max = 100)
            String sourceColumn,

            @NotBlank @Size(max = 200)
            String displayName,

            FilterType filterType,

            boolean sortable) {

        public ColumnRequest {
            if (filterType == null) {
                filterType = FilterType.NONE;
            }
        }
    }
}
