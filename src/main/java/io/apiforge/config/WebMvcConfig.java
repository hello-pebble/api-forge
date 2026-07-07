package io.apiforge.config;

import io.apiforge.web.ApiKeyAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 데이터 질의 경로에만 API 키 인터셉터를 건다.
 * 카탈로그(/api/v1/datasets, 정확히 일치)는 패턴에 걸리지 않아 공개로 유지된다.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final ApiKeyAuthInterceptor apiKeyAuthInterceptor;

    public WebMvcConfig(ApiKeyAuthInterceptor apiKeyAuthInterceptor) {
        this.apiKeyAuthInterceptor = apiKeyAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(apiKeyAuthInterceptor)
                .addPathPatterns("/api/v1/datasets/*");
    }
}
