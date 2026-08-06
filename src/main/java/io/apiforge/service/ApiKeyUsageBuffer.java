package io.apiforge.service;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 사용량 카운터의 메모리 버퍼 (DB 접근 없음).
 *
 * 조회 요청마다 UPDATE 를 두 번 날리면 GET 인데도 쓰기가 발생하고,
 * 특히 총계 갱신은 동일 API 키 행에 락이 몰려 직렬화된다.
 * 여기에 모았다가 {@link ApiKeyUsageFlusher} 가 주기적으로 한 번에 반영한다.
 */
@Component
public class ApiKeyUsageBuffer {

    /** 집계 단위 */
    public record UsageKey(Long apiKeyId, String datasetKey, LocalDate date) {
    }

    /** 반영 대상 스냅샷 */
    public record PendingUsage(UsageKey key, long delta, LocalDateTime lastUsedAt) {
    }

    private final ConcurrentMap<UsageKey, AtomicLong> counters = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, AtomicLong> lastUsedEpochMillis = new ConcurrentHashMap<>();

    public void record(Long apiKeyId, String datasetKey, LocalDate date, LocalDateTime now) {
        counters.computeIfAbsent(new UsageKey(apiKeyId, datasetKey, date), k -> new AtomicLong())
                .incrementAndGet();
        long millis = now.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        lastUsedEpochMillis.computeIfAbsent(apiKeyId, k -> new AtomicLong())
                .accumulateAndGet(millis, Math::max);
    }

    /**
     * 누적분을 꺼내 0으로 되돌린다.
     *
     * 엔트리를 제거하지 않고 getAndSet(0) 으로 비우는 이유: 제거와 동시에 들어온 증가가
     * 버려진 카운터에 반영돼 유실되는 것을 막기 위해서다. 대신 오늘이 아닌 날짜의
     * 0 엔트리는 여기서 정리해 무한히 쌓이지 않게 한다.
     */
    public List<PendingUsage> drain() {
        List<PendingUsage> pending = new ArrayList<>();
        Map<Long, LocalDateTime> lastUsed = new HashMap<>();

        LocalDate today = LocalDate.now();
        for (Map.Entry<UsageKey, AtomicLong> entry : counters.entrySet()) {
            long delta = entry.getValue().getAndSet(0);
            if (delta == 0) {
                if (!entry.getKey().date().equals(today)) {
                    counters.remove(entry.getKey(), entry.getValue());
                }
                continue;
            }
            Long apiKeyId = entry.getKey().apiKeyId();
            LocalDateTime seen = lastUsed.computeIfAbsent(apiKeyId, this::takeLastUsed);
            pending.add(new PendingUsage(entry.getKey(), delta, seen));
        }
        return pending;
    }

    private LocalDateTime takeLastUsed(Long apiKeyId) {
        AtomicLong holder = lastUsedEpochMillis.get(apiKeyId);
        long millis = holder == null ? 0 : holder.get();
        return millis == 0
                ? LocalDateTime.now()
                : LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), java.time.ZoneId.systemDefault());
    }
}
