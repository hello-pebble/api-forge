package io.apiforge.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apiforge.config.DataInitializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API 키 발급·인증·폐기와 사용량 통계 검증.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiKeyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ── 인증 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("키 없이 데이터 질의 시 401")
    void missingKey() throws Exception {
        mockMvc.perform(get("/api/v1/datasets/bills"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("잘못된 키는 401")
    void invalidKey() throws Exception {
        mockMvc.perform(get("/api/v1/datasets/bills").header("X-API-Key", "totally-wrong-key-xxxxxxxxxxxx"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("데모 키로는 정상 조회 200")
    void demoKeyWorks() throws Exception {
        mockMvc.perform(get("/api/v1/datasets/bills").header("X-API-Key", DataInitializer.DEMO_API_KEY))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("카탈로그는 키 없이도 공개")
    void catalogPublic() throws Exception {
        mockMvc.perform(get("/api/v1/datasets"))
                .andExpect(status().isOk());
    }

    // ── 관리자 키 관리 ───────────────────────────────────────────

    @Test
    @DisplayName("키 관리 API는 인증 필요")
    void adminKeysRequireAuth() throws Exception {
        mockMvc.perform(get("/admin/api/keys"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("발급 → 사용 → 사용량 집계 → 폐기 → 401 전체 흐름")
    void issueUseStatsRevoke() throws Exception {
        // 1) 발급 — 원문 키는 응답에서만 노출
        String issueResponse = mockMvc.perform(post("/admin/api/keys")
                        .with(httpBasic("admin", "admin1234"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"통합테스트 키\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rawKey").exists())
                .andExpect(jsonPath("$.keyPrefix").exists())
                .andReturn().getResponse().getContentAsString();

        JsonNode issued = objectMapper.readTree(issueResponse);
        String rawKey = issued.get("rawKey").asText();
        String prefix = issued.get("keyPrefix").asText();

        // 2) 발급 키로 데이터 질의 2회
        mockMvc.perform(get("/api/v1/datasets/bills").header("X-API-Key", rawKey))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/datasets/bills").header("X-API-Key", rawKey)
                        .param("COMMITTEE", "행정안전위원회"))
                .andExpect(status().isOk());

        // 3) 사용량 통계 — 총 2회, 데이터셋 bills 집계
        mockMvc.perform(get("/admin/api/keys/" + prefix + "/usage")
                        .with(httpBasic("admin", "admin1234")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keyPrefix").value(prefix))
                .andExpect(jsonPath("$.totalRequests").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.daily[0].datasetKey").value("bills"))
                .andExpect(jsonPath("$.daily[0].requestCount").value(greaterThanOrEqualTo(2)));

        // 4) 목록에 노출 (원문/해시 없이)
        mockMvc.perform(get("/admin/api/keys").with(httpBasic("admin", "admin1234")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.keyPrefix=='" + prefix + "')].label").value("통합테스트 키"));

        // 5) 폐기 후에는 401
        mockMvc.perform(post("/admin/api/keys/" + prefix + "/revoke")
                        .with(httpBasic("admin", "admin1234")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/datasets/bills").header("X-API-Key", rawKey))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("label 없는 발급 요청은 400")
    void issueRequiresLabel() throws Exception {
        mockMvc.perform(post("/admin/api/keys")
                        .with(httpBasic("admin", "admin1234"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
