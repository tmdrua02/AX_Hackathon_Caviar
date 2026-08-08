# CapStone Design Caviar · MedAssist

2026 캡스톤 디자인 캐비어 개발팀의 Android 의약품 안전 도우미입니다.

- Android 앱: `android/`
- Kotlin/Spring Boot 4 백엔드: `backend/`
- 백엔드 실행·공공데이터 설정·API 테스트: [backend/README.md](backend/README.md)

의약품 검색 결과는 LLM이 생성하지 않습니다. 식품의약품안전처 공공 API가 반환한 제품만 후보로 사용하며, 조회 실패와 조회 결과 없음은 서로 다른 상태로 처리합니다.

Android는 공식 ITEM_SEQ와 STTEMNT_NO를 확정한 뒤 backend의 동기식 `POST /api/v1/supplement-interaction-checks`를 호출합니다. deterministic severity를 그대로 표시하며 LLM explanation으로 판정을 변경하지 않습니다. emulator의 backend 주소는 `http://10.0.2.2:8080/`이고 OpenAI·공공데이터 키는 앱에 포함하지 않습니다. 상세 계약은 [Android integration contract](backend/docs/android-integration-contract.md)를 참고하세요.
