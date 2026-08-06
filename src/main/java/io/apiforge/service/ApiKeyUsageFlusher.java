package io.apiforge.service;

import io.apiforge.domain.ApiKeyUsageDaily;
import io.apiforge.repository.ApiKeyRepository;
import io.apiforge.repository.ApiKeyUsageDailyRepository;
import io.apiforge.service.ApiKeyUsageBuffer.PendingUsage;
import jakarta.annotation.PreDestroy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 버퍼에 모인 사용량을 주기적으로 DB에 반영한다.
 *
 * 트랜잭션 경계를 @Transactional 대신 TransactionTemplate 으로 잡은 이유:
 * 스케줄러·종료 훅(@PreDestroy)·관리자 조회 등 호출 경로가 섞여 있어
 * 프록시를 거치지 않는 자기호출에서도 동일하게 동작해야 하기 때문이다.
 */
@Component
public class ApiKeyUsageFlusher {

    private final ApiKeyUsageBuffer buffer;
    private final ApiKeyRepository apiKeyRepository;
    private final ApiKeyUsageDailyRepository usageRepository;
    private final TransactionTemplate transactionTemplate;

    public ApiKeyUsageFlusher(ApiKeyUsageBuffer buffer,
                              ApiKeyRepository apiKeyRepository,
                              ApiKeyUsageDailyRepository usageRepository,
                              PlatformTransactionManager transactionManager) {
        this.buffer = buffer;
        this.apiKeyRepository = apiKeyRepository;
        this.usageRepository = usageRepository;
        // REQUIRES_NEW — 관리자 통계 조회(readOnly 트랜잭션) 안에서 호출돼도
        // 쓰기를 별도 트랜잭션으로 분리해 커밋한다.
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Scheduled(fixedDelayString = "${apiforge.usage.flush-interval-ms:5000}")
    public void scheduledFlush() {
        flush();
    }

    @PreDestroy
    public void flushOnShutdown() {
        flush();
    }

    /** 관리자 통계 조회 직전에도 호출해 읽기 일관성(read-your-writes)을 보장한다. */
    public void flush() {
        List<PendingUsage> pending = buffer.drain();
        if (pending.isEmpty()) {
            return;
        }
        transactionTemplate.executeWithoutResult(status -> apply(pending));
    }

    private void apply(List<PendingUsage> pending) {
        Map<Long, Long> totalsByKey = new HashMap<>();
        Map<Long, java.time.LocalDateTime> lastUsedByKey = new HashMap<>();

        for (PendingUsage p : pending) {
            Long apiKeyId = p.key().apiKeyId();
            upsertDaily(p);
            totalsByKey.merge(apiKeyId, p.delta(), Long::sum);
            lastUsedByKey.merge(apiKeyId, p.lastUsedAt(), (a, b) -> b.isAfter(a) ? b : a);
        }
        // 키당 UPDATE 1회 — 요청 수와 무관하게 고정된다
        totalsByKey.forEach((apiKeyId, delta) ->
                apiKeyRepository.addRequests(apiKeyId, delta, lastUsedByKey.get(apiKeyId)));
    }

    /** 조건부 UPDATE 후 없으면 INSERT. 경합에 진 쪽은 다시 UPDATE 로 회수. */
    private void upsertDaily(PendingUsage p) {
        int updated = usageRepository.increment(
                p.key().apiKeyId(), p.key().datasetKey(), p.key().date(), p.delta());
        if (updated > 0) {
            return;
        }
        try {
            usageRepository.save(new ApiKeyUsageDaily(
                    apiKeyRepository.getReferenceById(p.key().apiKeyId()),
                    p.key().datasetKey(), p.key().date(), p.delta()));
        } catch (DataIntegrityViolationException raceLost) {
            usageRepository.increment(
                    p.key().apiKeyId(), p.key().datasetKey(), p.key().date(), p.delta());
        }
    }
}
