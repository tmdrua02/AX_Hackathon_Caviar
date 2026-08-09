# Google Compute Engine 해커톤 시연 배포

이 문서는 Ubuntu LTS 단일 VM(2 vCPU, RAM 4 GB 권장)에 Caddy, 메인 Spring Boot 서버, 약품 Spring Boot 서버를 Docker Compose로 배포하는 절차다. Cloud Run, 외부 DB, Supabase는 사용하지 않는다. GCE VM·디스크·고정 IP·네트워크 송신에는 Google Cloud 과금이 발생할 수 있으므로 시연 후 리소스를 정리한다.

## 1. 배포 구조

```text
Android APK
  └─ http://고정IP 또는 https://api.example.com
       └─ Caddy (:80/:443만 공개)
            ├─ /api/v1/drug-products/**                 → backend:8081
            ├─ /api/v1/supplement-products/**           → backend:8081
            ├─ /api/v1/supplement-interaction-checks/** → backend:8081
            └─ 나머지 /api/** 및 SSE                    → server:8080
                 ├─ H2 file DB                          → named volume
                 └─ 녹음 원본                           → named volume
```

`8080`, `8081`에는 호스트 포트 매핑이 없다. 컨테이너끼리는 전용 `app-internal` 네트워크로 통신하고, OpenAI·공공데이터·ACME 호출을 위해 별도 `egress` 네트워크에도 연결된다. Caddy는 `/api/**` 요청의 `X-Demo-Api-Key`를 검사한다. 이 토큰은 해커톤 시연용 최소 접근 통제이며 APK에서 추출 가능하므로 정식 사용자 인증을 대체하지 않는다.

## 2. GCE VM 준비

권장값:

- OS: Ubuntu 24.04 LTS x86_64
- 머신: `e2-medium`(2 vCPU, 4 GB RAM)
- 부팅 디스크: Balanced persistent disk 30 GB 이상
- 외부 IP: 리전 고정 IP
- 네트워크 태그: `medassist-demo`
- 공개 인바운드: TCP 80, 443만 전체 허용
- SSH: 가능하면 IAP를 사용하거나 본인 공인 IP `/32`로만 제한

Cloud Console에서 위 값으로 생성해도 되고, 아래 예시를 로컬 `gcloud`에서 실행해도 된다. 리전·존·프로젝트 이름은 실제 값으로 바꾼다. 이 프로젝트의 자동화는 클라우드 리소스를 직접 생성하지 않는다.

```bash
gcloud config set project YOUR_PROJECT_ID

gcloud compute addresses create medassist-demo-ip \
  --region=asia-northeast3

gcloud compute addresses describe medassist-demo-ip \
  --region=asia-northeast3 \
  --format='value(address)'

gcloud compute instances create medassist-demo \
  --zone=asia-northeast3-a \
  --machine-type=e2-medium \
  --image-family=ubuntu-2404-lts-amd64 \
  --image-project=ubuntu-os-cloud \
  --boot-disk-size=30GB \
  --boot-disk-type=pd-balanced \
  --address=YOUR_STATIC_EXTERNAL_IP \
  --tags=medassist-demo

gcloud compute firewall-rules create medassist-demo-web \
  --network=default \
  --direction=INGRESS \
  --action=ALLOW \
  --rules=tcp:80,tcp:443 \
  --source-ranges=0.0.0.0/0 \
  --target-tags=medassist-demo
```

`8080`, `8081` 허용 규칙은 만들지 않는다. SSH 규칙을 직접 만들 경우 `0.0.0.0/0` 대신 관리자의 현재 공인 IP만 사용한다.

참고: [GCE 고정 외부 IP](https://cloud.google.com/compute/docs/ip-addresses/configure-static-external-ip-address), [VPC 방화벽 규칙](https://cloud.google.com/firewall/docs/using-firewalls)

## 3. Docker Engine과 소스 설치

VM에 SSH로 접속한 뒤 Docker 공식 apt 저장소를 사용한다.

```bash
sudo apt update
sudo apt install -y ca-certificates curl git
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc

echo \
  "Types: deb
URIs: https://download.docker.com/linux/ubuntu
Suites: $(. /etc/os-release && echo "${UBUNTU_CODENAME:-$VERSION_CODENAME}")
Components: stable
Architectures: $(dpkg --print-architecture)
Signed-By: /etc/apt/keyrings/docker.asc" | \
  sudo tee /etc/apt/sources.list.d/docker.sources > /dev/null

sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo systemctl enable --now docker containerd
sudo usermod -aG docker "$USER"
newgrp docker

docker version
docker compose version
```

`docker` 그룹은 사실상 root 수준 권한을 준다. 시연 VM의 신뢰할 수 있는 관리자 계정에만 적용한다. 공식 절차: [Docker Engine on Ubuntu](https://docs.docker.com/engine/install/ubuntu/), [Linux post-install](https://docs.docker.com/engine/install/linux-postinstall/)

소스는 SSH deploy key 또는 본인의 GitHub 인증을 사용해 받는다. 토큰을 clone URL에 직접 넣지 않는다.

```bash
git clone https://github.com/tmdrua02/AX_Hackathon_Caviar.git
cd AX_Hackathon_Caviar
git switch 'feat/녹음기록AI+동시복용AI-연결'
```

## 4. 운영 환경변수

```bash
cp .env.example .env
chmod 600 .env
nano .env
```

필수 설정:

```dotenv
SERVER_ADDRESS=http://YOUR_STATIC_EXTERNAL_IP
DEMO_API_TOKEN=openssl_rand_hex_32_result
DB_USERNAME=sa
DB_PASSWORD=another_openssl_rand_hex_32_result
OPENAI_API_KEY=sk-...
DATA_GO_KR_SERVICE_KEY=...
```

임의값은 각각 따로 생성한다.

```bash
openssl rand -hex 32
openssl rand -hex 32
```

- OpenAI 키와 공공데이터 키는 `.env`에서 컨테이너로만 주입된다.
- Android에는 공급자 키를 넣지 않는다.
- `.env`는 `.gitignore` 대상이며 `docker compose config` 전체 출력도 비밀값을 펼쳐 보여줄 수 있으므로 공유하거나 CI 로그에 남기지 않는다.
- `SERVER_DB_VOLUME`, `RECORDINGS_VOLUME`은 복구 시 외에는 기본값을 유지한다.

## 5. IP 기반 HTTP 1차 배포와 검증

도메인 연결 전에는 `.env`의 주소를 반드시 `http://고정IP` 형태로 둔다. IP만 적으면 Caddy가 로컬 인증서를 사용하려 할 수 있다.

```bash
docker compose -f compose.prod.yml --env-file .env config --quiet
docker compose -f compose.prod.yml --env-file .env build --pull
docker compose -f compose.prod.yml --env-file .env up -d
docker compose -f compose.prod.yml --env-file .env ps
```

health 확인:

```bash
curl --fail http://YOUR_STATIC_EXTERNAL_IP/actuator/health
curl --fail http://YOUR_STATIC_EXTERNAL_IP/health
```

인증 확인과 메인 API 확인:

```bash
curl -i http://YOUR_STATIC_EXTERNAL_IP/api/v1/home

curl --fail \
  -H 'X-Demo-Api-Key: YOUR_DEMO_API_TOKEN' \
  http://YOUR_STATIC_EXTERNAL_IP/api/v1/home
```

첫 호출은 `401`, 두 번째 호출은 `200`이어야 한다. 약품 API 경로도 같은 공개 주소와 헤더를 사용한다.

녹음 업로드/상태 확인:

```bash
curl --fail \
  -H 'X-Demo-Api-Key: YOUR_DEMO_API_TOKEN' \
  -H 'Idempotency-Key: demo-upload-001' \
  -F 'audio=@test.m4a;type=audio/mp4' \
  -F 'title=시연 진료' \
  -F 'hospitalName=해커톤 병원' \
  -F 'consultedAt=2026-08-09T12:00:00Z' \
  -F 'durationMs=90000' \
  http://YOUR_STATIC_EXTERNAL_IP/api/v1/consultations

curl --fail \
  -H 'X-Demo-Api-Key: YOUR_DEMO_API_TOKEN' \
  http://YOUR_STATIC_EXTERNAL_IP/api/v1/consultations/RESOURCE_ID
```

업로드 응답은 `202`이고 상태는 `QUEUED → RUNNING → SUCCEEDED` 순서다. OpenAI 설정·네트워크 문제가 있으면 `FAILED`와 안전한 `failureCode/failureMessage`가 저장된다. 같은 `Idempotency-Key`를 다시 보내면 동일 리소스를 돌려준다.

SSE 확인:

```bash
curl --fail \
  -H 'X-Demo-Api-Key: YOUR_DEMO_API_TOKEN' \
  -H 'Content-Type: application/json' \
  -d '{"message":"타이레놀 복용 시 주의사항을 알려줘"}' \
  http://YOUR_STATIC_EXTERNAL_IP/api/v1/chat/sessions/SESSION_ID/messages

curl -N \
  -H 'X-Demo-Api-Key: YOUR_DEMO_API_TOKEN' \
  http://YOUR_STATIC_EXTERNAL_IP/api/v1/chat/sessions/SESSION_ID/stream
```

세션은 먼저 `POST /api/v1/chat/sessions`로 만든다. Caddy의 `flush_interval -1` 설정이 중간 버퍼링 없이 이벤트를 전달한다.

## 6. Android 빌드 설정

### 에뮬레이터/로컬 서버

기본 debug 값은 기존 동작을 유지한다.

- 메인 서버: `http://10.0.2.2:8080/`
- 약품 backend: `http://10.0.2.2:8081/`
- cleartext: debug 빌드에서만 허용

### 실제 스마트폰에서 GCE IP 테스트

두 API URL 모두 Caddy의 한 주소로 설정한다.

```bash
./gradlew :android:app:assembleDebug \
  -PDEBUG_API_BASE_URL=http://YOUR_STATIC_EXTERNAL_IP/ \
  -PDEBUG_SUPPLEMENT_API_BASE_URL=http://YOUR_STATIC_EXTERNAL_IP/ \
  -PDEBUG_DEMO_API_TOKEN=YOUR_DEMO_API_TOKEN
```

APK 위치:

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

### HTTPS 릴리스 빌드

릴리스 URL은 반드시 `https://`여야 하며 cleartext가 차단된다. 두 서버 URL은 동일한 공개 주소를 사용한다.

```bash
./gradlew :android:app:assembleRelease \
  -PRELEASE_API_BASE_URL=https://api.example.com/ \
  -PRELEASE_SUPPLEMENT_API_BASE_URL=https://api.example.com/ \
  -PRELEASE_DEMO_API_TOKEN=YOUR_DEMO_API_TOKEN
```

CLI의 release APK는 서명 설정이 없다면 배포용으로 설치할 수 없다. Android Studio의 **Build > Generate Signed App Bundle or APK**에서 별도 keystore로 서명하고, keystore와 비밀번호는 저장소에 커밋하지 않는다. 공급자 API 키는 어떤 Gradle 속성에도 넣지 않는다.

## 7. 도메인과 HTTPS 전환

1. DNS 제공자에서 `api.example.com`의 A 레코드를 GCE 고정 IP로 지정한다.
2. 외부에서 DNS 전파를 확인한다.
3. `.env`를 `SERVER_ADDRESS=api.example.com`으로 변경한다.
4. Caddy를 재생성한다.

```bash
docker compose -f compose.prod.yml --env-file .env up -d --force-recreate caddy
docker compose -f compose.prod.yml --env-file .env logs --tail=100 caddy
curl --fail https://api.example.com/actuator/health
```

Caddy는 도메인이 설정되고 80/443이 외부에서 도달 가능하며 `/data` 볼륨이 유지되면 인증서 발급·갱신과 HTTP→HTTPS 전환을 자동 처리한다. 공식 조건: [Caddy HTTPS quick-start](https://caddyserver.com/docs/quick-starts/https), [Automatic HTTPS](https://caddyserver.com/docs/automatic-https)

## 8. 운영, 업데이트, 백업, 복구

상태와 로그:

```bash
docker compose -f compose.prod.yml --env-file .env ps
docker compose -f compose.prod.yml --env-file .env logs --tail=200 server
docker compose -f compose.prod.yml --env-file .env logs --tail=200 backend
docker compose -f compose.prod.yml --env-file .env logs --tail=200 caddy
df -h
docker system df
```

로그는 컨테이너별 10 MB × 3개로 회전한다. request body, OpenAI 키, 공공데이터 키를 로그에 쓰지 않는다.

코드 업데이트:

```bash
git status
git pull --ff-only
docker compose -f compose.prod.yml --env-file .env build --pull
docker compose -f compose.prod.yml --env-file .env up -d
docker compose -f compose.prod.yml --env-file .env ps
```

업데이트 전 백업:

```bash
./scripts/backup-prod.sh
```

이 스크립트는 H2 일관성을 위해 메인 서버만 잠시 graceful stop하고 DB·녹음 named volume을 각각 압축하며 SHA-256 파일을 만든 뒤 서버를 다시 시작한다. 백업 디렉터리는 `backups/UTC_TIMESTAMP/`이고 Git에서 제외된다. VM 장애에도 대비하려면 암호화한 사본을 별도 안전한 저장소에 보관한다.

기존 볼륨을 삭제하지 않는 복구 방법:

```bash
docker compose -f compose.prod.yml --env-file .env down
docker volume create medassist-demo-server-db-restored
docker volume create medassist-demo-recordings-restored

docker run --rm \
  -v medassist-demo-server-db-restored:/restore \
  -v "$PWD/backups/YOUR_BACKUP_TIMESTAMP:/backup:ro" \
  alpine:3.21 tar -C /restore -xzf /backup/server-db.tar.gz

docker run --rm \
  -v medassist-demo-recordings-restored:/restore \
  -v "$PWD/backups/YOUR_BACKUP_TIMESTAMP:/backup:ro" \
  alpine:3.21 tar -C /restore -xzf /backup/recordings.tar.gz
```

`.env`의 두 값을 바꾼 뒤 시작한다.

```dotenv
SERVER_DB_VOLUME=medassist-demo-server-db-restored
RECORDINGS_VOLUME=medassist-demo-recordings-restored
```

```bash
docker compose -f compose.prod.yml --env-file .env up -d
```

문제가 있으면 `.env`의 볼륨 이름을 기존 값으로 되돌려 롤백한다. 사용하지 않는 이미지 정리는 동작 확인 후에만 수행한다.

```bash
docker image prune -f
```

named volume 삭제와 `docker system prune --volumes`는 DB·녹음을 지울 수 있으므로 실행하지 않는다.

## 9. 장애 대응 체크리스트

- `401`: APK 빌드의 `*_DEMO_API_TOKEN`과 VM `.env`의 `DEMO_API_TOKEN`이 같은지 확인하고 Caddy를 재생성한다.
- `413`: 녹음 비트레이트/길이를 확인한다. Caddy와 Spring 제한은 기본 32 MB/30 MB다. 제한을 무작정 늘리면 4 GB VM의 메모리·디스크 위험이 커진다.
- `502/503`: `docker compose ... ps`와 해당 서비스의 최근 로그를 확인한다. 8080/8081을 외부에 열지 않는다.
- `FAILED / OPENAI_NOT_CONFIGURED`: `OPENAI_API_KEY`가 VM의 `.env`에 있는지 확인하고 `server`를 재생성한다. 키 자체는 출력하지 않는다.
- `STORAGE_FULL`: `df -h`, `docker system df`, 백업 존재 여부를 확인한 뒤 오래된 이미지·로그만 정리한다. DB/녹음 volume은 삭제하지 않는다.
- HTTPS 발급 실패: DNS A 레코드, 고정 IP, TCP 80/443 방화벽, Caddy 로그를 확인한다.
- SSE가 한 번에 몰려 옴: 클라이언트는 `curl -N` 또는 스트리밍 reader를 사용하고, 중간 프록시를 추가했다면 buffering을 끈다.
- 재부팅 후 장애: `sudo systemctl status docker`, `docker compose ... ps`를 확인한다. Docker는 부팅 시 시작하고 서비스는 `restart: unless-stopped`로 복구된다.

이 구성은 단일 VM·단일 H2 writer를 전제로 한 해커톤 시연용이다. 다중 인스턴스, 무중단 DB failover, 사용자별 강한 인증, 감사 로그, 규정 준수가 필요한 실제 의료 서비스 운영 구성은 아니다.
