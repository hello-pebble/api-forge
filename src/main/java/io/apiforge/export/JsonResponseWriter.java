package io.apiforge.export;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.apiforge.query.QueryResult;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class JsonResponseWriter implements ResponseWriter {

    private final ObjectMapper objectMapper;

    public JsonResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String format() {
        return "json";
    }

    @Override
    public String contentType() {
        return "application/json;charset=UTF-8";
    }

    @Override
    public void write(QueryResult result, OutputStream out) throws IOException {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("datasetKey", result.dataset().getDatasetKey());
        envelope.put("page", result.page());
        envelope.put("size", result.size());
        envelope.put("totalCount", result.totalCount());
        envelope.put("data", result.rows());
        objectMapper.writeValue(out, envelope);
    }
}
