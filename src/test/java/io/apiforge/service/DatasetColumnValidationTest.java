package io.apiforge.service;

import io.apiforge.config.DataInitializer;
import io.apiforge.domain.Dataset;
import io.apiforge.domain.DatasetColumn;
import io.apiforge.domain.FilterType;
import io.apiforge.repository.DatasetRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 소스 칼럼 중복 검증.
 *
 * 중복을 허용하면 등록·발행은 200 으로 통과하지만 조회는 jOOQ 가
 * "Field ... is not unique in Record" 로 거부해 전 포맷이 500 이 된다.
 * 관리자에게는 성공으로 보이고 소비자만 깨지므로 등록 시점에 막아야 한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DatasetColumnValidationTest {

    private static final String KEY = DataInitializer.DEMO_API_KEY;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DatasetRepository datasetRepository;

    @Test
    @DisplayName("같은 소스 칼럼을 두 번 등록하면 400")
    void duplicateColumnRejectedOnCreate() throws Exception {
        mockMvc.perform(post("/admin/api/datasets")
                        .with(httpBasic("admin", "admin1234"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "datasetKey": "dup-create",
                                  "name": "중복 칼럼",
                                  "sourceTable": "NA_BILL",
                                  "columns": [
                                    {"sourceColumn": "BILL_ID", "displayName": "번호1", "filterType": "EQUALS", "sortable": true},
                                    {"sourceColumn": "BILL_ID", "displayName": "번호2", "filterType": "WORDS", "sortable": false}
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("같은 소스 칼럼을 두 번 등록할 수 없습니다")));

        // 등록 자체가 없었어야 한다 — 반쯤 만들어진 데이터셋이 남으면 안 된다
        mockMvc.perform(get("/admin/api/datasets/dup-create").with(httpBasic("admin", "admin1234")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("대소문자만 다른 칼럼도 중복으로 본다 — 조회 시 매칭이 대소문자 무시이므로")
    void caseInsensitiveDuplicateRejected() throws Exception {
        mockMvc.perform(post("/admin/api/datasets")
                        .with(httpBasic("admin", "admin1234"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "datasetKey": "dup-case",
                                  "name": "대소문자 중복",
                                  "sourceTable": "NA_BILL",
                                  "columns": [
                                    {"sourceColumn": "BILL_ID", "displayName": "번호1", "filterType": "EQUALS", "sortable": true},
                                    {"sourceColumn": "bill_id", "displayName": "번호2", "filterType": "NONE", "sortable": false}
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("검증 이전에 저장된 중복 칼럼 데이터셋은 발행 단계에서 막힌다")
    void duplicateColumnRejectedOnPublish() throws Exception {
        // 서비스 검증을 우회해 직접 저장 — 수정 이전에 만들어진 데이터를 재현한다
        Dataset legacy = new Dataset("dup-legacy", "레거시 중복", null, "NA_BILL");
        legacy.addColumn(new DatasetColumn("BILL_ID", "번호1", FilterType.EQUALS, true));
        legacy.addColumn(new DatasetColumn("BILL_ID", "번호2", FilterType.NONE, false));
        datasetRepository.save(legacy);

        mockMvc.perform(post("/admin/api/datasets/dup-legacy/publish")
                        .with(httpBasic("admin", "admin1234")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("같은 소스 칼럼을 두 번 등록할 수 없습니다")));

        // 발행되지 않았으므로 공개 API 에도 노출되지 않는다
        mockMvc.perform(get("/api/v1/datasets/dup-legacy").header("X-API-Key", KEY))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/admin/api/datasets/dup-legacy").with(httpBasic("admin", "admin1234")))
                .andExpect(status().isNoContent());
    }
}
