package io.apiforge.domain;

/**
 * 칼럼별 필터 유형. 레거시 시스템의 필터 코드(CHECK/DATE/WORDS/기본)를 계승한다.
 */
public enum FilterType {
    /** 필터 불가 — 조회 결과에만 노출 */
    NONE,
    /** 단일 값 일치 (=) */
    EQUALS,
    /** 텍스트 부분 검색 (LIKE %keyword%) */
    WORDS,
    /** 다중 선택 (IN) — 콤마 구분 값 */
    CHECK,
    /** 날짜 범위 (BETWEEN) — "from,to" 형식 */
    DATE
}
