-- 샘플 소스 데이터: 의안 정보 (데모용 가상 데이터, 공공데이터 예시)
-- 관리자가 이 테이블을 대상으로 데이터셋 메타데이터를 등록하면
-- 코드 수정 없이 /api/v1/datasets/{key} Open API가 생성된다.
--
-- 식별자를 큰따옴표로 감싸 대문자로 고정한다.
-- (PostgreSQL은 미인용 식별자를 소문자로, H2/Oracle은 대문자로 접기 때문에
--  jOOQ가 생성하는 인용 대문자 이름과 일치시키기 위한 이식성 조치)

CREATE TABLE IF NOT EXISTS "NA_BILL" (
    "BILL_ID"     VARCHAR(20)  PRIMARY KEY,
    "BILL_NM"     VARCHAR(300) NOT NULL,
    "PROPOSER"    VARCHAR(100) NOT NULL,
    "COMMITTEE"   VARCHAR(100),
    "PROPOSE_DT"  DATE         NOT NULL,
    "BILL_STATUS" VARCHAR(30)  NOT NULL
);

DELETE FROM "NA_BILL";

INSERT INTO "NA_BILL" VALUES ('2200001', '공공데이터의 제공 및 이용 활성화에 관한 법률 일부개정법률안', '김민준 의원 등 12인', '행정안전위원회', DATE '2026-01-15', '위원회 심사');
INSERT INTO "NA_BILL" VALUES ('2200002', '개인정보 보호법 일부개정법률안', '이서연 의원 등 10인', '정무위원회', DATE '2026-01-22', '위원회 심사');
INSERT INTO "NA_BILL" VALUES ('2200003', '인공지능 산업 육성 및 신뢰 기반 조성에 관한 법률안', '박지훈 의원 등 15인', '과학기술정보방송통신위원회', DATE '2026-02-03', '본회의 통과');
INSERT INTO "NA_BILL" VALUES ('2200004', '소득세법 일부개정법률안', '최수아 의원 등 11인', '기획재정위원회', DATE '2026-02-10', '위원회 심사');
INSERT INTO "NA_BILL" VALUES ('2200005', '전자정부법 일부개정법률안', '정도윤 의원 등 13인', '행정안전위원회', DATE '2026-02-18', '접수');
INSERT INTO "NA_BILL" VALUES ('2200006', '청년기본법 일부개정법률안', '강하은 의원 등 10인', '여성가족위원회', DATE '2026-03-02', '위원회 심사');
INSERT INTO "NA_BILL" VALUES ('2200007', '데이터 산업진흥 및 이용촉진에 관한 기본법 일부개정법률안', '김민준 의원 등 14인', '과학기술정보방송통신위원회', DATE '2026-03-11', '본회의 통과');
INSERT INTO "NA_BILL" VALUES ('2200008', '국민건강보험법 일부개정법률안', '윤재현 의원 등 12인', '보건복지위원회', DATE '2026-03-25', '위원회 심사');
INSERT INTO "NA_BILL" VALUES ('2200009', '중소기업창업 지원법 일부개정법률안', '임소율 의원 등 10인', '산업통상자원중소벤처기업위원회', DATE '2026-04-07', '접수');
INSERT INTO "NA_BILL" VALUES ('2200010', '도로교통법 일부개정법률안', '한지우 의원 등 11인', '행정안전위원회', DATE '2026-04-16', '위원회 심사');
INSERT INTO "NA_BILL" VALUES ('2200011', '기후위기 대응을 위한 탄소중립·녹색성장 기본법 일부개정법률안', '오세린 의원 등 16인', '환경노동위원회', DATE '2026-05-06', '위원회 심사');
INSERT INTO "NA_BILL" VALUES ('2200012', '고등교육법 일부개정법률안', '서준호 의원 등 10인', '교육위원회', DATE '2026-05-20', '접수');
INSERT INTO "NA_BILL" VALUES ('2200013', '전기통신사업법 일부개정법률안', '박지훈 의원 등 12인', '과학기술정보방송통신위원회', DATE '2026-06-04', '위원회 심사');
INSERT INTO "NA_BILL" VALUES ('2200014', '주택임대차보호법 일부개정법률안', '문가영 의원 등 13인', '법제사법위원회', DATE '2026-06-17', '접수');
INSERT INTO "NA_BILL" VALUES ('2200015', '국가재정법 일부개정법률안', '최수아 의원 등 10인', '기획재정위원회', DATE '2026-06-30', '접수');
