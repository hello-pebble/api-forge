package io.apiforge.web;

import io.apiforge.domain.ApiKey;
import io.apiforge.service.ApiKeyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 데이터 질의 엔드포인트(/api/v1/datasets/{key})에 API 키 인증을 강제하고,
 * 성공 응답에 한해 사용량을 집계한다. 카탈로그(/api/v1/datasets)는 대상이 아니다.
 */
@Component
public class ApiKeyAuthInterceptor implements HandlerInterceptor {

    static final String API_KEY_HEADER = "X-API-Key";
    private static final String RESOLVED_KEY_ID = "apiforge.apiKeyId";

    private final ApiKeyService apiKeyService;

    public ApiKeyAuthInterceptor(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 인증 실패 시 ApiKeyException → GlobalExceptionHandler 에서 401 변환
        ApiKey key = apiKeyService.authenticate(request.getHeader(API_KEY_HEADER));
        request.setAttribute(RESOLVED_KEY_ID, key.getId());
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        Object keyId = request.getAttribute(RESOLVED_KEY_ID);
        boolean success = response.getStatus() >= 200 && response.getStatus() < 300;
        if (keyId == null || !success) {
            return;
        }
        Object vars = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (vars instanceof Map<?, ?> map) {
            Object datasetKey = ((Map<String, String>) map).get("datasetKey");
            if (datasetKey != null) {
                apiKeyService.recordUsage((Long) keyId, datasetKey.toString(),
                        LocalDate.now(), LocalDateTime.now());
            }
        }
    }
}
