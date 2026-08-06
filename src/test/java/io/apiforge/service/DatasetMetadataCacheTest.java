package io.apiforge.service;

import io.apiforge.config.DataInitializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 메타데이터 캐시 무효화 검증.
 *
 * 캐시는 조회 경로의 JPA 접근을 없애지만, 관리 작업 후에도 옛 메타데이터가 남으면
 * 삭제된 데이터셋이 계속 서비스되거나 발행이 반영되지 않는다. 각 관리 작업 뒤에
 * 캐시가 비워지는지를 캐시가 이미 채워진 상태에서 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DatasetMetadataCacheTest {

    private static final String KEY = DataInitializer.DEMO_API_KEY;

    private static final String BODY = """
            {
              "datasetKey": "cache-probe",
              "name": "캐시 검증용",
              "sourceTable": "NA_BILL",
              "columns": [
                {"sourceColumn": "BILL_ID", "displayName": "의안번호", "filterType": "EQUALS", "sortable": true}
              ]
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("발행·삭제 후 캐시가 무효화되어 카탈로그와 질의에 즉시 반영된다")
    void invalidatedOnPublishAndDelete() throws Exception {
        // 캐시를 미리 채운다 — 이 시점의 카탈로그에는 cache-probe 가 없다
        mockMvc.perform(get("/api/v1/datasets")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/datasets/cache-probe").header("X-API-Key", KEY))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/admin/api/datasets")
                        .with(httpBasic("admin", "admin1234"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/admin/api/datasets/cache-probe/publish")
                        .with(httpBasic("admin", "admin1234")))
                .andExpect(status().isOk());

        // 미발행 상태를 캐싱해 둔 뒤 발행했더라도 즉시 보여야 한다
        mockMvc.perform(get("/api/v1/datasets/cache-probe").header("X-API-Key", KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(15));
        mockMvc.perform(get("/api/v1/datasets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.datasetKey=='cache-probe')].status").value("PUBLISHED"));

        mockMvc.perform(delete("/admin/api/datasets/cache-probe")
                        .with(httpBasic("admin", "admin1234")))
                .andExpect(status().isNoContent());

        // 삭제 후에는 캐시에 남아 서비스되면 안 된다
        mockMvc.perform(get("/api/v1/datasets/cache-probe").header("X-API-Key", KEY))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/datasets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.datasetKey=='cache-probe')]").isEmpty());
    }
}
