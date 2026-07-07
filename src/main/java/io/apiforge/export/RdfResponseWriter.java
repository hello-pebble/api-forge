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

/**
 * RDF/XML 포맷 Writer (시맨틱 웹/링크드 데이터 소비용).
 *
 * 각 행을 rdf:Description 리소스로, 칼럼을 데이터셋 네임스페이스의 속성으로 표현한다.
 * 칼럼명은 등록 시 식별자 규칙([A-Za-z][A-Za-z0-9_]*)이 검증되어 안전한 XML/속성명이다.
 */
@Component
public class RdfResponseWriter implements ResponseWriter {

    @Override
    public String format() {
        return "rdf";
    }

    @Override
    public String contentType() {
        return "application/rdf+xml;charset=UTF-8";
    }

    @Override
    public void write(QueryResult result, OutputStream out) throws IOException {
        String datasetKey = result.dataset().getDatasetKey();
        String base = "urn:apiforge:" + datasetKey;

        Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
        writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        writer.write("<rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\"\n");
        writer.write("         xmlns:d=\"" + base + "#\">\n");

        int index = 0;
        for (Map<String, Object> row : result.rows()) {
            writer.write("  <rdf:Description rdf:about=\"" + base + "/row/" + index + "\">\n");
            for (DatasetColumn col : result.dataset().getColumns()) {
                Object value = row.get(col.getSourceColumn());
                writer.write("    <d:" + col.getSourceColumn() + ">"
                        + escape(value == null ? "" : String.valueOf(value))
                        + "</d:" + col.getSourceColumn() + ">\n");
            }
            writer.write("  </rdf:Description>\n");
            index++;
        }

        writer.write("</rdf:RDF>\n");
        writer.flush();
    }

    private String escape(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
