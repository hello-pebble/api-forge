package io.apiforge.web;

import io.apiforge.domain.DatasetStatus;
import io.apiforge.export.ResponseWriter;
import io.apiforge.export.ResponseWriterResolver;
import io.apiforge.query.QueryResult;
import io.apiforge.repository.DatasetRepository;
import io.apiforge.service.DataQueryService;
import io.apiforge.web.dto.DatasetView;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
    private final DatasetRepository datasetRepository;
    private final ResponseWriterResolver writerResolver;

    public OpenApiController(DataQueryService dataQueryService,
                             DatasetRepository datasetRepository,
                             ResponseWriterResolver writerResolver) {
        this.dataQueryService = dataQueryService;
        this.datasetRepository = datasetRepository;
        this.writerResolver = writerResolver;
    }

    /** 발행된 데이터셋 카탈로그 — 사용 가능한 필터·정렬 칼럼 메타데이터 포함 */
    @GetMapping
    @Transactional(readOnly = true)
    public List<DatasetView> catalog() {
        return datasetRepository.findAllByStatus(DatasetStatus.PUBLISHED).stream()
                .map(DatasetView::from)
                .toList();
    }

    /** 데이터 질의 — 단일 엔드포인트에서 필터·정렬·페이징·포맷 처리 */
    @GetMapping("/{datasetKey}")
    public void query(@PathVariable String datasetKey,
                      @RequestParam Map<String, String> params,
                      @RequestParam(defaultValue = "0") int page,
                      @RequestParam(defaultValue = "20") int size,
                      @RequestParam(required = false) String sort,
                      @RequestParam(defaultValue = "json") String format,
                      HttpServletResponse response) throws IOException {

        ResponseWriter writer = writerResolver.resolve(format);

        Map<String, String> filters = new LinkedHashMap<>(params);
        RESERVED_PARAMS.forEach(filters::remove);

        QueryResult result = dataQueryService.query(datasetKey, filters, sort, page, size);

        response.setContentType(writer.contentType());
        writer.write(result, response.getOutputStream());
    }
}
