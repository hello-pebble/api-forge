package io.apiforge.config;

import io.apiforge.domain.FilterType;
import io.apiforge.repository.DatasetRepository;
import io.apiforge.service.DatasetAdminService;
import io.apiforge.web.dto.DatasetCreateRequest;
import io.apiforge.web.dto.DatasetCreateRequest.ColumnRequest;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 데모 시드 — "의안 정보" 데이터셋을 등록·발행한다.
 * 관리자 API로 등록하는 것과 동일한 경로를 타므로 그 자체가 사용 예시다.
 */
@Configuration
public class DataInitializer {

    @Bean
    public ApplicationRunner seedSampleDataset(DatasetAdminService adminService, DatasetRepository repository) {
        return args -> {
            if (repository.existsByDatasetKey("bills")) {
                return;
            }
            adminService.create(new DatasetCreateRequest(
                    "bills",
                    "의안 정보",
                    "발의된 의안의 기본 정보 (데모용 가상 데이터, 공공데이터 예시)",
                    "NA_BILL",
                    List.of(
                            new ColumnRequest("BILL_ID", "의안번호", FilterType.EQUALS, true),
                            new ColumnRequest("BILL_NM", "의안명", FilterType.WORDS, false),
                            new ColumnRequest("PROPOSER", "대표발의", FilterType.WORDS, false),
                            new ColumnRequest("COMMITTEE", "소관위원회", FilterType.CHECK, true),
                            new ColumnRequest("PROPOSE_DT", "발의일자", FilterType.DATE, true),
                            new ColumnRequest("BILL_STATUS", "처리상태", FilterType.CHECK, true))));
            adminService.publish("bills");
        };
    }
}
