package io.apiforge.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 실제 PostgreSQL 컨테이너에 대한 동적 API 엔진 검증.
 *
 * H2 인메모리로 검증한 필터·정렬·포맷·보안 동작이 실 RDBMS에서도
 * 동일하게 성립함을 증명한다 (다중 DB 이식성). Docker가 없는 환경에서는
 * 자동으로 스킵된다.
 *
 * 확인 포인트:
 * - jOOQ 동적 쿼리가 PostgreSQL 방언으로 정상 렌더링·실행되는가
 * - 식별자 대소문자 이식성(인용 대문자)이 유지되는가
 * - 값 바인딩 기반 인젝션 방어가 PostgreSQL에서도 유효한가
 */
@SpringBootTest(properties = {
        "spring.sql.init.mode=always",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class PostgresIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("PostgreSQL 컨테이너가 기동되고 데이터소스로 연결된다")
    void containerIsRunning() {
        org.assertj.core.api.Assertions.assertThat(postgres.isRunning()).isTrue();
    }

    @Test
    @DisplayName("발행 데이터셋 카탈로그 조회 — 시드가 실 DB에 적재됨")
    void catalog() throws Exception {
        mockMvc.perform(get("/api/v1/datasets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].datasetKey").value("bills"));
    }

    @Test
    @DisplayName("기본 조회 — 전체 15건")
    void queryAll() throws Exception {
        mockMvc.perform(get("/api/v1/datasets/bills"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(15));
    }

    @Test
    @DisplayName("CHECK 필터 — 소관위원회 (PostgreSQL IN)")
    void checkFilter() throws Exception {
        mockMvc.perform(get("/api/v1/datasets/bills").param("COMMITTEE", "행정안전위원회"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(3));
    }

    @Test
    @DisplayName("WORDS 필터 — 부분 검색 (PostgreSQL ILIKE)")
    void wordsFilter() throws Exception {
        mockMvc.perform(get("/api/v1/datasets/bills").param("BILL_NM", "데이터"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(2));
    }

    @Test
    @DisplayName("DATE 필터 — 날짜 범위 (PostgreSQL BETWEEN)")
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
    @DisplayName("SQL Injection 시도 값은 바인드 파라미터로 처리되어 0건 — PostgreSQL에서도 유효")
    void injectionValueIsHarmless() throws Exception {
        mockMvc.perform(get("/api/v1/datasets/bills").param("BILL_ID", "' OR '1'='1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(0));
    }

    @Test
    @DisplayName("등록되지 않은 필터 파라미터는 400")
    void unregisteredFilterRejected() throws Exception {
        mockMvc.perform(get("/api/v1/datasets/bills").param("EVIL", "1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("CSV 포맷 — 표시명 헤더")
    void csvFormat() throws Exception {
        mockMvc.perform(get("/api/v1/datasets/bills").param("format", "csv"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("의안번호")));
    }
}
