package io.apiforge.export;

import io.apiforge.domain.DatasetColumn;
import io.apiforge.query.QueryResult;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

/**
 * Excel(.xlsx) 포맷 Writer. 대용량 대비 SXSSF 스트리밍으로 메모리 상주 행을 제한한다.
 */
@Component
public class ExcelResponseWriter implements ResponseWriter {

    /** 메모리에 유지하는 행 수 (초과분은 임시파일로 flush) */
    private static final int WINDOW_SIZE = 200;

    @Override
    public String format() {
        return "excel";
    }

    @Override
    public String contentType() {
        return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    }

    @Override
    public void write(QueryResult result, OutputStream out) throws IOException {
        List<DatasetColumn> columns = result.dataset().getColumns();
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(WINDOW_SIZE)) {
            Sheet sheet = workbook.createSheet(result.dataset().getDatasetKey());

            // 헤더 — 표시명, 볼드
            CellStyle headerStyle = workbook.createCellStyle();
            Font bold = workbook.createFont();
            bold.setBold(true);
            headerStyle.setFont(bold);

            Row header = sheet.createRow(0);
            for (int c = 0; c < columns.size(); c++) {
                Cell cell = header.createCell(c);
                cell.setCellValue(columns.get(c).getDisplayName());
                cell.setCellStyle(headerStyle);
            }

            int rowIdx = 1;
            for (Map<String, Object> row : result.rows()) {
                Row excelRow = sheet.createRow(rowIdx++);
                for (int c = 0; c < columns.size(); c++) {
                    Object value = row.get(columns.get(c).getSourceColumn());
                    excelRow.createCell(c).setCellValue(value == null ? "" : String.valueOf(value));
                }
            }

            workbook.write(out);
            workbook.dispose(); // 임시파일 정리
        }
    }
}
