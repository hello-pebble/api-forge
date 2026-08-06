package io.apiforge.service;

import io.apiforge.config.DataInitializer;
import io.apiforge.domain.Dataset;
import io.apiforge.domain.DatasetStatus;
import io.apiforge.repository.DatasetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 메타데이터 캐시 검증.
 *
 * 캐시는 조회 경로의 JPA 접근을 없애지만, 잘못 만들면 두 가지로 무너진다.
 * (1) 관리 작업 후에도 옛 메타데이터가 남아 삭제된 데이터셋이 계속 서비스된다
 * (2) 캐시가 무한히 커져 메모리를 고갈시킨다
 * 두 축을 모두 검증한다.
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

    @Autowired
    private DatasetMetadataCache cache;

    @MockitoSpyBean
    private DatasetRepository datasetRepository;

    @BeforeEach
    void resetCache() {
        cache.invalidate();
        clearInvocations(datasetRepository);
    }

    // ── 무효화 ───────────────────────────────────────────────────

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

        // 미발행 상태를 조회해 둔 뒤 발행했더라도 즉시 보여야 한다
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

    // ── 캐시 크기 ────────────────────────────────────────────────

    @Test
    @DisplayName("존재하는 데이터셋만 캐싱한다 — 없는 키는 캐시를 늘리지 않는다")
    void missesAreNotCached() {
        // 없는 키: 매번 DB 를 다시 본다 (캐싱하면 임의 키 호출만으로 맵이 무한히 커진다)
        cache.findPublished("no-such-dataset");
        cache.findPublished("no-such-dataset");
        verify(datasetRepository, times(2))
                .findWithColumnsByDatasetKeyAndStatus(eq("no-such-dataset"), eq(DatasetStatus.PUBLISHED));

        // 있는 키: 두 번째부터는 DB 를 보지 않는다
        clearInvocations(datasetRepository);
        assertThat(cache.findPublished("bills")).isPresent();
        assertThat(cache.findPublished("bills")).isPresent();
        verify(datasetRepository, times(1))
                .findWithColumnsByDatasetKeyAndStatus(eq("bills"), eq(DatasetStatus.PUBLISHED));
    }

    // ── 무효화 경쟁 ──────────────────────────────────────────────

    @Test
    @DisplayName("DB 를 읽는 도중 무효화가 일어나면 읽어온 값을 캐시에 남기지 않는다")
    void loadRacingWithInvalidationIsDiscarded() {
        // 스파이가 반환할 실제 값을 미리 확보한다 (인터페이스 스파이라 callRealMethod 는 못 쓴다)
        Optional<Dataset> real =
                datasetRepository.findWithColumnsByDatasetKeyAndStatus("bills", DatasetStatus.PUBLISHED);
        assertThat(real).isPresent();

        // 조회 스레드가 DB 를 읽는 사이에 관리 작업이 캐시를 비우는 상황을 재현한다.
        // 무효화는 반드시 다른 스레드에서 일으킨다 — 같은 트랜잭션 안에서 부르면
        // afterCompletion 이 저장 이후에 실행돼 경쟁이 재현되지 않는다.
        doAnswer(invocation -> {
            CompletableFuture.runAsync(cache::invalidate).join();
            return real;
        }).when(datasetRepository)
                .findWithColumnsByDatasetKeyAndStatus(eq("bills"), eq(DatasetStatus.PUBLISHED));

        assertThat(cache.findPublished("bills")).isPresent();

        // 값이 캐시에 남았다면 다음 호출은 DB 를 보지 않는다 → 남지 않았어야 한다
        clearInvocations(datasetRepository);
        assertThat(cache.findPublished("bills")).isPresent();
        verify(datasetRepository, times(1))
                .findWithColumnsByDatasetKeyAndStatus(eq("bills"), eq(DatasetStatus.PUBLISHED));
    }
}
