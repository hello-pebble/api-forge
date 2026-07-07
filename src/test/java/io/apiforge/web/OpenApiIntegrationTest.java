package io.apiforge.web;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 시드된 "의안 정보(bills)" 데이터셋에 대한 E2E 검증.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    // ── 카탈로그 & 기본 조회 ──────────────────────────────────────

    @Test
    @DisplayName("발행된 데이터셋 카탈로그를 공개 조회할 수 있다")
    void catalog() throws Exception {
        mockMvc.perform(get("/api/v1/datasets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].datasetKey").value("bills"))
                .andExpect(jsonPath("$[0].columns[0].sourceColumn").value("BILL_ID"));
    }

    @Test
    @DisplayName("기본 조회 — 전체 건수와 페이징 정보를 반환한다")
    void queryAll() throws Exception {
        mockMvc.perform(get("/api/v1/datasets/bills"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(15))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.data.length()").value(15));
    }

    @Test
    @DisplayName("존재하지 않는 데이터셋은 404")
    void unknownDataset() throws Exception {
        mockMvc.perform(get("/api/v1/datasets/nope"))
                .andExpect(status().isNotFound());
    }

    // ── 필터 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("CHECK 필터 — 소관위원회 다중 선택")
    void checkFilter() throws Exception {
        mockMvc.perform(get("/api/v1/datasets/bills").param("COMMITTEE", "행정안전위원회"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(3));
    }

    @Test
    @DisplayName("WORDS 필터 — 의안명 부분 검색")
    void wordsFilter() throws Exception {
        mockMvc.perform(get("/api/v1/datasets/bills").param("BILL_NM", "데이터"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(2));
    }

    @Test
    @DisplayName("DATE 필터 — 발의일자 범위 조회")
    void dateFilter() throws Exception {
        mockMvc.perform(get("/api/v1/datasets/bills").param("PROPOSE_DT", "2026-01-01,2026-02-28"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(5));
    }

    @Test
    @DisplayName("정렬 — 발의일자 내림차순")
    void sortDesc() throws Exception {
        mockMvc.perform(get("/api/v1/datasets/bills").param("sort", "PROPOSE_DT,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].BILL_ID").value("2200015"));
    }

    @Test
    @DisplayName("페이징 — size/page 반영")
    void paging() throws Exception {
        mockMvc.perform(get("/api/v1/datasets/bills").param("size", "5").param("page", "1")
                        .param("sort", "BILL_ID"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(5))
                .andExpect(jsonPath("$.data[0].BILL_ID").value("2200006"));
    }

    // ── 보안 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("SQL Injection 시도 값은 바인드 파라미터로 처리되어 0건 반환")
    void injectionValueIsHarmless() throws Exception {
        mockMvc.perform(get("/api/v1/datasets/bills").param("BILL_ID", "' OR '1'='1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(0));
    }

    @Test
    @DisplayName("등록되지 않은 필터 파라미터는 400 — 화이트리스트")
    void unregisteredFilterRejected() throws Exception {
        mockMvc.perform(get("/api/v1/datasets/bills").param("EVIL", "1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("관리자 API는 인증 없이 접근 불가")
    void adminRequiresAuth() throws Exception {
        mockMvc.perform(get("/admin/api/datasets"))
                .andExpect(status().isUnauthorized());
    }

    // ── 포맷 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("CSV 포맷 — 표시명 헤더 포함")
    void csvFormat() throws Exception {
        mockMvc.perform(get("/api/v1/datasets/bills").param("format", "csv"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(content().string(containsString("의안번호")));
    }

    @Test
    @DisplayName("XML 포맷")
    void xmlFormat() throws Exception {
        mockMvc.perform(get("/api/v1/datasets/bills").param("format", "xml"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                .andExpect(content().string(containsString("<BILL_ID>")));
    }

    @Test
    @DisplayName("지원하지 않는 포맷은 400")
    void unknownFormat() throws Exception {
        mockMvc.perform(get("/api/v1/datasets/bills").param("format", "yaml"))
                .andExpect(status().isBadRequest());
    }

    // ── 관리자 워크플로우 E2E ─────────────────────────────────────

    @Test
    @DisplayName("등록 → 발행 → 즉시 Open API 노출 — No-Code 파이프라인 E2E")
    void registerPublishQuery() throws Exception {
        String body = """
                {
                  "datasetKey": "bills-mini",
                  "name": "의안 요약",
                  "description": "칼럼 축소판",
                  "sourceTable": "NA_BILL",
                  "columns": [
                    {"sourceColumn": "BILL_ID", "displayName": "의안번호", "filterType": "EQUALS", "sortable": true},
                    {"sourceColumn": "BILL_NM", "displayName": "의안명", "filterType": "WORDS", "sortable": false}
                  ]
                }
                """;

        mockMvc.perform(post("/admin/api/datasets")
                        .with(httpBasic("admin", "admin1234"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        // 발행 전에는 포털에 노출되지 않음
        mockMvc.perform(get("/api/v1/datasets/bills-mini"))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/admin/api/datasets/bills-mini/publish")
                        .with(httpBasic("admin", "admin1234")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        // 발행 즉시 조회 가능 — 배포 불필요
        mockMvc.perform(get("/api/v1/datasets/bills-mini"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(15))
                .andExpect(jsonPath("$.data[0].COMMITTEE").doesNotExist());

        mockMvc.perform(delete("/admin/api/datasets/bills-mini")
                        .with(httpBasic("admin", "admin1234")))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("존재하지 않는 소스 테이블은 발행이 거부된다")
    void publishWithBadTableRejected() throws Exception {
        String body = """
                {
                  "datasetKey": "broken",
                  "name": "깨진 데이터셋",
                  "sourceTable": "NO_SUCH_TABLE",
                  "columns": [
                    {"sourceColumn": "X", "displayName": "x", "filterType": "NONE", "sortable": false}
                  ]
                }
                """;

        mockMvc.perform(post("/admin/api/datasets")
                        .with(httpBasic("admin", "admin1234"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/admin/api/datasets/broken/publish")
                        .with(httpBasic("admin", "admin1234")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/admin/api/datasets/broken")
                        .with(httpBasic("admin", "admin1234")))
                .andExpect(status().isNoContent());
    }
}
