package io.apiforge.export;

import io.apiforge.query.QueryResult;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * XML 포맷 Writer.
 * 엘리먼트명은 등록 시 식별자 규칙([A-Za-z][A-Za-z0-9_]*)이 검증된 칼럼명만 사용하므로 안전하다.
 */
@Component
public class XmlResponseWriter implements ResponseWriter {

    @Override
    public String format() {
        return "xml";
    }

    @Override
    public String contentType() {
        return "application/xml;charset=UTF-8";
    }

    @Override
    public void write(QueryResult result, OutputStream out) throws IOException {
        Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
        writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        writer.write("<response>\n");
        writer.write("  <datasetKey>" + escape(result.dataset().getDatasetKey()) + "</datasetKey>\n");
        writer.write("  <page>" + result.page() + "</page>\n");
        writer.write("  <size>" + result.size() + "</size>\n");
        writer.write("  <totalCount>" + result.totalCount() + "</totalCount>\n");
        writer.write("  <rows>\n");
        for (Map<String, Object> row : result.rows()) {
            writer.write("    <row>\n");
            for (var col : result.dataset().getColumns()) {
                Object value = row.get(col.getSourceColumn());
                writer.write("      <" + col.getSourceColumn() + ">"
                        + escape(value == null ? "" : String.valueOf(value))
                        + "</" + col.getSourceColumn() + ">\n");
            }
            writer.write("    </row>\n");
        }
        writer.write("  </rows>\n");
        writer.write("</response>\n");
        writer.flush();
    }

    private String escape(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
