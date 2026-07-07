package io.apiforge.web;

import io.apiforge.repository.DatasetRepository;
import io.apiforge.service.DatasetAdminService;
import io.apiforge.web.dto.DatasetCreateRequest;
import io.apiforge.web.dto.DatasetView;
import io.apiforge.web.error.DatasetNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 관리자 API — 데이터셋 메타데이터 등록·발행·삭제.
 * 발행 즉시 /api/v1/datasets/{key} 로 노출된다 (배포 불필요).
 */
@RestController
@RequestMapping("/admin/api/datasets")
public class AdminDatasetController {

    private final DatasetAdminService adminService;
    private final DatasetRepository datasetRepository;

    public AdminDatasetController(DatasetAdminService adminService, DatasetRepository datasetRepository) {
        this.adminService = adminService;
        this.datasetRepository = datasetRepository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<DatasetView> list() {
        return datasetRepository.findAll().stream().map(DatasetView::from).toList();
    }

    @GetMapping("/{datasetKey}")
    @Transactional(readOnly = true)
    public DatasetView get(@PathVariable String datasetKey) {
        return datasetRepository.findByDatasetKey(datasetKey)
                .map(DatasetView::from)
                .orElseThrow(() -> new DatasetNotFoundException(datasetKey));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DatasetView create(@RequestBody @Valid DatasetCreateRequest request) {
        return DatasetView.from(adminService.create(request));
    }

    @PostMapping("/{datasetKey}/publish")
    public DatasetView publish(@PathVariable String datasetKey) {
        return DatasetView.from(adminService.publish(datasetKey));
    }

    @DeleteMapping("/{datasetKey}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String datasetKey) {
        adminService.delete(datasetKey);
    }
}
