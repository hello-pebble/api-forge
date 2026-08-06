package io.apiforge.web;

import io.apiforge.config.DataInitializer;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 시드된 "의안 정보(bills)" 데이터셋에 대한 E2E 검증.
 * 데이터 질의에는 데모 API 키를 헤더로 전달한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiIntegrationTest {

    private static final String KEY = DataInitializer.DEMO_API_KEY;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    /** CSV 인용 검증용 픽스처 — 데모 시드를 건드리지 않도록 테스트에서만 만든다. */
    private void createCsvQuoteSource() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS \"CSV_QUOTE_SRC\" (\"VAL\" VARCHAR(50))");
        jdbcTemplate.execute("DELETE FROM \"CSV_QUOTE_SRC\"");
        jdbcTemplate.update("INSERT INTO \"CSV_QUOTE_SRC\" VALUES (?)", "a,b");
        jdbcTemplate.update("INSERT INTO \"CSV_QUOTE_SRC\" VALUES (?)", "\"q\"");
        jdbcTemplate.update("INSERT INTO \"CSV_QUOTE_SRC\" VALUES (?)", "cr\rhere");
    }

    // ── 카탈로그 & 기본 조회 ──────────────────────────────────────

    @Test
    @DisplayName("발행된 데이터셋 카탈로그는 키 없이 공개 조회할 수 있다")
    void catalog() throws Exception {
        mockMvc.perform(get("/api/v1/datasets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].datasetKey").value("bills"))
                .andExpect(jsonPath("$[0].columns[0].sourceColumn").value("BILL_ID"));
    }

    @Test
    @DisplayName("기본 조회 — 전체 건수와 페이징 정보를 반환한다")
    void queryAll() throws Exception {
        mockMvc.perform(get("/api/v1/datasets/bills").header("X-API-Key", KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(15))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.data.length()").value(15));
    }

    @Test
    @DisplayName("존재하지 않는 데이터셋은 404")
    void unknownDataset() throws Exception {
        mockMvc.perform(get("/api/v1/datasets/nope").header("X-API-Key", KEY))
                .andExpect(status().isNotFound());
    }

    // ── 필터 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("CHECK 필터 — 소관위원회 다중 선택")
    void checkFilter() throws Exception {
        mockMvc.perform(get("/api/v1/datasets/bills").header("X-API-Key", KEY).param("COMMITTEE", "행정안전위원회"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(3));
    }

    @Test
    @DisplayName("WORDS 필터 — 의안명 부분 검색")
    void wordsFilter() throws Exception {
        mockMvc.perform(get("/api/v1/datasets/bills").header("X-API-Key", KEY).param("BILL_NM", "데이터"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(2));
    }

    @Test
    @DisplayName("DATE 필터 — 발의일자 범위 조회")
    void dateFilter() throws Exception {
        mockMvc.perform(get("/api/v1/datasets/bills").header("X-API-Key", KEY).param("PROPOSE_DT", "2026-01-01,2026-02-28"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(5));
    }

    @Test
    @DisplayName("정렬 — 발의일자 내림차순")
    void sortDesc() throws Exception {
        mockMvc.perform(get("/api/v1/datasets/bills").header("X-API-Key", KEY).param("sort", "PROPOSE_DT,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].BILL_ID").value("2200015"));
    }

    @Test
    @DisplayName("페이징 — size/page 반영")
    void paging() throws Exception {
        mockMvc.perform(get("/api/v1/datasets/bills").header("X-API-Key", KEY)
                        .param("size", "5").param("page", "1").param("sort", "BILL_ID"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(5))
                .andExpect(jsonPath("$.data[0].BILL_ID").value("2200006"));
    }

    @Test
    @DisplayName("page * size 오버플로는 500이 아니라 400")
    void pagingOverflowRejected() throws Exception {
        mockMvc.perform(get("/api/v1/datasets/bills").header("X-API-Key", KEY)
                        .param("page", String.valueOf(Integer.MAX_VALUE)).param("size", "100"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("페이지 범위를 벗어났습니다")));
    }

    @Test
    @DisplayName("OFFSET 상한 경계는 정상 처리 — 데이터가 없어 빈 결과")
    void pagingAtOffsetBoundary() throws Exception {
        // page * size 가 Integer.MAX_VALUE 를 넘지 않는 최대 지점
        mockMvc.perform(get("/api/v1/datasets/bills").header("X-API-Key", KEY)
                        .param("page", String.valueOf(Integer.MAX_VALUE / 100)).param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(15))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @DisplayName("음수 page 는 0으로 보정된다")
    void negativePageClamped() throws Exception {
        mockMvc.perform(get("/api/v1/datasets/bills").header("X-API-Key", KEY)
                        .param("page", "-5").param("size", "1").param("sort", "BILL_ID"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.data[0].BILL_ID").value("2200001"));
    }

    // ── 보안 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("SQL Injection 시도 값은 바인드 파라미터로 처리되어 0건 반환")
    void injectionValueIsHarmless() throws Exception {
        mockMvc.perform(get("/api/v1/datasets/bills").header("X-API-Key", KEY).param("BILL_ID", "' OR '1'='1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(0));
    }

    @Test
    @DisplayName("등록되지 않은 필터 파라미터는 400 — 화이트리스트")
    void unregisteredFilterRejected() throws Exception {
        mockMvc.perform(get("/api/v1/datasets/bills").header("X-API-Key", KEY).param("EVIL", "1"))
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
        mockMvc.perform(get("/api/v1/datasets/bills").header("X-API-Key", KEY).param("format", "csv"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(content().string(containsString("의안번호")));
    }

    @Test
    @DisplayName("XML 포맷")
    void xmlFormat() throws Exception {
        mockMvc.perform(get("/api/v1/datasets/bills").header("X-API-Key", KEY).param("format", "xml"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                .andExpect(content().string(containsString("<BILL_ID>")));
    }

    @Test
    @DisplayName("Excel 포맷 — xlsx(zip) 바이너리 반환")
    void excelFormat() throws Exception {
        byte[] body = mockMvc.perform(get("/api/v1/datasets/bills").header("X-API-Key", KEY).param("format", "excel"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andReturn().getResponse().getContentAsByteArray();
        // xlsx 는 zip 컨테이너 — 매직 넘버 'PK'
        org.assertj.core.api.Assertions.assertThat(body.length).isGreaterThan(0);
        org.assertj.core.api.Assertions.assertThat(body[0]).isEqualTo((byte) 'P');
        org.assertj.core.api.Assertions.assertThat(body[1]).isEqualTo((byte) 'K');
    }

    @Test
    @DisplayName("RDF 포맷 — RDF/XML 리소스 반환")
    void rdfFormat() throws Exception {
        mockMvc.perform(get("/api/v1/datasets/bills").header("X-API-Key", KEY).param("format", "rdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/rdf+xml"))
                .andExpect(content().string(containsString("<rdf:RDF")))
                .andExpect(content().string(containsString("rdf:Description")))
                .andExpect(content().string(containsString("<d:BILL_ID>")));
    }

    @Test
    @DisplayName("지원하지 않는 포맷은 400")
    void unknownFormat() throws Exception {
        mockMvc.perform(get("/api/v1/datasets/bills").header("X-API-Key", KEY).param("format", "yaml"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("파일 포맷에는 Content-Disposition 첨부 헤더가 붙는다")
    void downloadFormatsHaveAttachmentHeader() throws Exception {
        mockMvc.perform(get("/api/v1/datasets/bills").header("X-API-Key", KEY).param("format", "csv"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Content-Disposition", containsString("bills.csv")));

        mockMvc.perform(get("/api/v1/datasets/bills").header("X-API-Key", KEY).param("format", "excel"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("bills.xlsx")));
    }

    @Test
    @DisplayName("브라우저에서 열리는 포맷에는 Content-Disposition 을 붙이지 않는다")
    void inlineFormatsHaveNoAttachmentHeader() throws Exception {
        for (String format : new String[] {"json", "xml", "rdf"}) {
            mockMvc.perform(get("/api/v1/datasets/bills").header("X-API-Key", KEY).param("format", format))
                    .andExpect(status().isOk())
                    .andExpect(header().doesNotExist("Content-Disposition"));
        }
    }

    @Test
    @DisplayName("CSV — 콤마·따옴표·개행(CR 단독 포함)이 든 값은 인용된다")
    void csvQuotesSpecialCharacters() throws Exception {
        // 값에 CR 이 섞이면 인용하지 않을 경우 행이 갈라진다
        createCsvQuoteSource();

        mockMvc.perform(post("/admin/api/datasets")
                        .with(httpBasic("admin", "admin1234"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "datasetKey": "csv-quote",
                                  "name": "CSV 인용 검증",
                                  "sourceTable": "CSV_QUOTE_SRC",
                                  "columns": [
                                    {"sourceColumn": "VAL", "displayName": "값", "filterType": "NONE", "sortable": false}
                                  ]
                                }
                                """))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/admin/api/datasets/csv-quote/publish")
                        .with(httpBasic("admin", "admin1234")))
                .andExpect(status().isOk());

        String csv = mockMvc.perform(get("/api/v1/datasets/csv-quote")
                        .header("X-API-Key", KEY).param("format", "csv"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(csv)
                .contains("\"a,b\"")            // 콤마
                .contains("\"\"\"q\"\"\"")      // 따옴표 이스케이프
                .contains("\"cr\rhere\"");      // CR 단독

        mockMvc.perform(delete("/admin/api/datasets/csv-quote")
                        .with(httpBasic("admin", "admin1234")))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("같은 필터 파라미터를 중복 지정하면 조용히 무시하지 않고 400")
    void duplicateFilterParamRejected() throws Exception {
        mockMvc.perform(get("/api/v1/datasets/bills").header("X-API-Key", KEY)
                        .param("BILL_ID", "2200001").param("BILL_ID", "2200002"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("여러 번 지정할 수 없습니다")));
    }

    @Test
    @DisplayName("예약 파라미터 중복은 필터 검사 대상이 아니다")
    void duplicateReservedParamIgnored() throws Exception {
        mockMvc.perform(get("/api/v1/datasets/bills").header("X-API-Key", KEY)
                        .param("size", "5").param("size", "5"))
                .andExpect(status().isOk());
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

        // 발행 전에는 포털에 노출되지 않음 (키는 유효)
        mockMvc.perform(get("/api/v1/datasets/bills-mini").header("X-API-Key", KEY))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/admin/api/datasets/bills-mini/publish")
                        .with(httpBasic("admin", "admin1234")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        // 발행 즉시 조회 가능 — 배포 불필요
        mockMvc.perform(get("/api/v1/datasets/bills-mini").header("X-API-Key", KEY))
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
