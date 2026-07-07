package io.apiforge.export;

import io.apiforge.domain.DatasetColumn;
import io.apiforge.query.QueryResult;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class CsvResponseWriter implements ResponseWriter {

    /** Excel 한글 호환을 위한 UTF-8 BOM */
    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    @Override
    public String format() {
        return "csv";
    }

    @Override
    public String contentType() {
        return "text/csv;charset=UTF-8";
    }

    @Override
    public void write(QueryResult result, OutputStream out) throws IOException {
        out.write(UTF8_BOM);
        Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);

        // 헤더 — 표시명 사용
        writer.write(result.dataset().getColumns().stream()
                .map(DatasetColumn::getDisplayName)
                .map(this::escape)
                .reduce((a, b) -> a + "," + b)
                .orElse(""));
        writer.write("\r\n");

        for (Map<String, Object> row : result.rows()) {
            writer.write(result.dataset().getColumns().stream()
                    .map(col -> escape(stringify(row.get(col.getSourceColumn()))))
                    .reduce((a, b) -> a + "," + b)
                    .orElse(""));
            writer.write("\r\n");
        }
        writer.flush();
    }

    private String stringify(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String escape(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
