package io.apiforge.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ApiKeyIssueRequest(
        @NotBlank @Size(max = 100) String label) {
}
