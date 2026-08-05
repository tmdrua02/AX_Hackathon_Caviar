# Security

- `DATA_GO_KR_SERVICE_KEY`는 서버 환경변수로만 관리한다.
- `.env`와 `application-local.*`는 Git에서 제외한다.
- 외부 요청 URL 및 query string wire logging을 활성화하지 않는다.
- 예외 응답에는 서비스키, OCR 원문, 외부 body를 포함하지 않는다.
- OCR 원문 전체를 일반 로그에 남기지 않는다.
- 처방전 이미지 기능을 구현할 때 MIME type과 크기를 검증하고 기본적으로 처리 직후 삭제한다.
- 운영 환경은 HTTPS를 강제한다.
- 사용자 기능을 구현할 때 모든 draft, medication, interaction ID 조회에 `user_id` 소유권 조건을 포함한다.
- 외부 API 원문 저장이 필요하면 서비스키가 포함된 요청 URL이 아니라 응답 body와 안전한 source metadata만 저장한다.
