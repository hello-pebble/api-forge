package io.apiforge.web.error;

/** 존재하지 않거나 발행되지 않은 데이터셋 → 404 */
public class DatasetNotFoundException extends RuntimeException {

    public DatasetNotFoundException(String datasetKey) {
        super("데이터셋을 찾을 수 없습니다: " + datasetKey);
    }
}
