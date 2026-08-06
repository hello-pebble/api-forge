package io.apiforge.web;

import io.apiforge.export.ResponseWriter;
import io.apiforge.export.ResponseWriterResolver;
import io.apiforge.query.QueryResult;
import io.apiforge.service.DataQueryService;
import io.apiforge.service.DatasetMetadataCache;
import io.apiforge.web.dto.DatasetView;
import io.apiforge.web.error.InvalidQueryException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.MultiValueMap;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 공개 Open API — 발행된 데이터셋 카탈로그 조회와 데이터 질의.
 *
 * 예약 파라미터(page, size, sort, format)를 제외한 나머지 쿼리 파라미터는
 * 등록된 칼럼에 대한 필터로 해석된다.
 */
@RestController
@RequestMapping("/api/v1/datasets")
public class OpenApiController {

    private static final Set<String> RESERVED_PARAMS = Set.of("page", "size", "sort", "format");

    private final DataQueryService dataQueryService;
    private final DatasetMetadataCache metadataCache;
    private final ResponseWriterResolver writerResolver;

    public OpenApiController(DataQueryService dataQueryService,
                             DatasetMetadataCache metadataCache,
                             ResponseWriterResolver writerResolver) {
        this.dataQueryService = dataQueryService;
        this.metadataCache = metadataCache;
        this.writerResolver = writerResolver;
    }

    /** 발행된 데이터셋 카탈로그 — 사용 가능한 필터·정렬 칼럼 메타데이터 포함 */
    @GetMapping
    public List<DatasetView> catalog() {
        return metadataCache.publishedCatalog().stream()
                .map(DatasetView::from)
                .toList();
    }

    /** 데이터 질의 — 단일 엔드포인트에서 필터·정렬·페이징·포맷 처리 */
    @GetMapping("/{datasetKey}")
    public void query(@PathVariable String datasetKey,
                      @RequestParam MultiValueMap<String, String> params,
                      @RequestParam(defaultValue = "0") int page,
                      @RequestParam(defaultValue = "20") int size,
                      @RequestParam(required = false) String sort,
                      @RequestParam(defaultValue = "json") String format,
                      HttpServletResponse response) throws IOException {

        ResponseWriter writer = writerResolver.resolve(format);
        Map<String, String> filters = filtersOf(params);

        QueryResult result = dataQueryService.query(datasetKey, filters, sort, page, size);

        response.setContentType(writer.contentType());
        applyDownloadHeader(response, writer, result);
        writer.write(result, response.getOutputStream());
    }

    /**
     * 예약 파라미터를 걷어내고 필터만 남긴다.
     *
     * 같은 파라미터가 여러 번 오면 조용히 첫 값만 쓰는 대신 400 으로 거부한다.
     * 무시하면 호출자는 자기 조건 일부가 빠진 줄 모른 채 결과를 신뢰하게 된다.
     */
    private Map<String, String> filtersOf(MultiValueMap<String, String> params) {
        Map<String, String> filters = new LinkedHashMap<>();
        params.forEach((name, values) -> {
            if (RESERVED_PARAMS.contains(name)) {
                return;
            }
            if (values.size() > 1) {
                throw new InvalidQueryException(
                        "같은 필터 파라미터를 여러 번 지정할 수 없습니다 (다중 값은 콤마로 구분하세요): " + name);
            }
            filters.put(name, values.get(0));
        });
        return filters;
    }

    /**
     * 파일로 내려받는 포맷에는 Content-Disposition 을 붙인다.
     * 파일명은 요청 경로가 아니라 조회에 성공한 데이터셋의 저장된 키를 쓴다
     * (등록 시 규칙이 검증된 값이라 헤더 인젝션 여지가 없다).
     */
    private void applyDownloadHeader(HttpServletResponse response, ResponseWriter writer, QueryResult result) {
        String extension = writer.downloadExtension();
        if (extension == null) {
            return;
        }
        String filename = result.dataset().getDatasetKey() + "." + extension;
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(filename).build().toString());
    }
}
