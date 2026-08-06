# Security

- `DATA_GO_KR_SERVICE_KEY`는 네 공공 API가 공통으로 사용하는 하나의 서버 환경변수로만 관리한다. 기존 환경변수 이름은 유지한다.
- `.env`와 `application-local.*`는 Git에서 제외한다.
- 외부 요청 URL 및 query string wire logging을 활성화하지 않는다.
- 예외 응답에는 서비스키, OCR 원문, 외부 body를 포함하지 않는다.
- OCR 원문 전체를 일반 로그에 남기지 않는다.
- 처방전 이미지 기능을 구현할 때 MIME type과 크기를 검증하고 기본적으로 처리 직후 삭제한다.
- 운영 환경은 HTTPS를 강제한다.
- 사용자 기능을 구현할 때 모든 draft, medication, interaction ID 조회에 `user_id` 소유권 조건을 포함한다.
- 외부 API 원문 저장이 필요하면 서비스키가 포함된 요청 URL이 아니라 응답 body와 안전한 source metadata만 저장한다.
- 공공 API 요청 URL 전체와 `serviceKey` query parameter를 로그에 남기지 않는다. 불가피하게 URI가 포함된 문자열은 공통 마스킹 함수를 거친다.
- HTTP client wire logging은 기본 비활성 상태로 유지한다.
- 서비스키가 비어 있어도 서버는 시작하되 실제 요청은 `PUBLIC_API_NOT_CONFIGURED`로 거부한다.
- 서비스키 설정 객체의 문자열 표현, 로그, 애플리케이션 예외 메시지에 키 원문을 포함하지 않는다.
- 공공 API 응답은 먼저 byte 배열로 받고 엄격한 charset decoder를 통과시킨다. 디코딩 오류나 replacement character를 정상 의료 데이터로 저장하지 않는다.
- JSON 응답에 charset이 없을 때만 UTF-8 기본값을 사용한다. EUC-KR이나 CP949를 실측 없이 추측해 고정하지 않는다.
- 실제 운영 응답의 `Content-Type` 및 charset 헤더는 서비스키가 준비된 수동 통합 테스트에서 확인한다.
