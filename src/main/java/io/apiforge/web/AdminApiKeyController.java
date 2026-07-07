package io.apiforge.web;

import io.apiforge.service.ApiKeyService;
import io.apiforge.service.ApiKeyService.IssuedKey;
import io.apiforge.web.dto.ApiKeyIssueRequest;
import io.apiforge.web.dto.ApiKeyUsageReport;
import io.apiforge.web.dto.ApiKeyView;
import io.apiforge.web.dto.IssuedApiKeyResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 관리자 API 키 관리 — 발급·목록·폐기·사용량 통계.
 * /admin/** 은 ADMIN 권한(HTTP Basic) 필요.
 */
@RestController
@RequestMapping("/admin/api/keys")
public class AdminApiKeyController {

    private final ApiKeyService apiKeyService;

    public AdminApiKeyController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IssuedApiKeyResponse issue(@RequestBody @Valid ApiKeyIssueRequest request) {
        IssuedKey issued = apiKeyService.issue(request.label());
        return IssuedApiKeyResponse.of(issued.rawKey(), issued.key().getKeyPrefix(), issued.key().getLabel());
    }

    @GetMapping
    public List<ApiKeyView> list() {
        return apiKeyService.list().stream().map(ApiKeyView::from).toList();
    }

    @PostMapping("/{keyPrefix}/revoke")
    public void revoke(@PathVariable String keyPrefix) {
        apiKeyService.revoke(keyPrefix);
    }

    @GetMapping("/{keyPrefix}/usage")
    public ApiKeyUsageReport usage(@PathVariable String keyPrefix) {
        return apiKeyService.usage(keyPrefix);
    }
}
