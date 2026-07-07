package io.apiforge.export;

import io.apiforge.web.error.InvalidQueryException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * format 파라미터 → Writer 매핑.
 * ResponseWriter 구현 빈을 전부 수집하므로 새 포맷 추가 시 코드 수정이 없다.
 */
@Component
public class ResponseWriterResolver {

    private final Map<String, ResponseWriter> writers;

    public ResponseWriterResolver(List<ResponseWriter> writerBeans) {
        this.writers = writerBeans.stream()
                .collect(Collectors.toMap(ResponseWriter::format, Function.identity()));
    }

    public ResponseWriter resolve(String format) {
        String key = (format == null || format.isBlank()) ? "json" : format.toLowerCase();
        ResponseWriter writer = writers.get(key);
        if (writer == null) {
            throw new InvalidQueryException(
                    "지원하지 않는 포맷입니다: " + format + " (지원: " + String.join(", ", writers.keySet()) + ")");
        }
        return writer;
    }
}
