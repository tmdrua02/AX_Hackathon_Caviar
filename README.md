# MediMate

MediMate는 진료 음성을 녹음해 OpenAI로 전사·화자 구분·구조화 요약을 만들고, 공공데이터 기반 의약품 검색과 성분·DUR 비교, 복용 알람, 복약 챗봇을 제공하는 Android/Spring Boot 프로젝트입니다.

## 프로젝트 구성

| 경로 | 런타임 | 역할 | 기본 포트 |
| --- | --- | --- | --- |
| `android/app` | Android 8.0(API 26)+, JDK 17 | Jetpack Compose 앱 | - |
| `server` | Java 21, Spring Boot 3 | 진료 녹음, OpenAI 전사·요약, 메디봇, 기록 저장 | `8080` |
| `backend` | Java 17, Kotlin, Spring Boot 4 | 공공 의약품·건강기능식품 검색 및 성분·DUR 분석 | `8081` |
| `medication-safety-wireframe` | Node.js 22+ | 선택적 UI 와이어프레임 | 별도 README 참고 |

Gradle과 Android Gradle Plugin 버전은 저장소의 Wrapper 및 Version Catalog에 고정되어 있습니다. 시스템 Gradle을 별도로 설치하지 말고 `./gradlew`를 사용하세요.

## 1. 클린 macOS 개발 환경 설치

### 1.1 기본 도구와 JDK 설치

Homebrew가 없다면 먼저 설치합니다.

```bash
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
```

Git, JDK 17, JDK 21, Android Studio를 설치합니다.

```bash
brew update
brew install git openjdk@17 openjdk@21
brew install --cask android-studio

sudo ln -sfn "$(brew --prefix openjdk@17)/libexec/openjdk.jdk" \
  /Library/Java/JavaVirtualMachines/openjdk-17.jdk
sudo ln -sfn "$(brew --prefix openjdk@21)/libexec/openjdk.jdk" \
  /Library/Java/JavaVirtualMachines/openjdk-21.jdk

/usr/libexec/java_home -V
```

- Android 앱과 `backend`: JDK 17
- `server`: JDK 21

### 1.2 Android SDK 설치

Android Studio를 한 번 실행한 뒤 `Settings > Languages & Frameworks > Android SDK`에서 다음 항목을 설치합니다.

- Android SDK Platform 35
- Android SDK Build-Tools 35.x
- Android SDK Platform-Tools
- Android Emulator
- Android SDK Command-line Tools (latest)
- Google APIs ARM 64 v8a System Image(API 35, Apple Silicon 기준)

표준 SDK 경로를 셸에 등록합니다. 사용하는 셸 설정 파일(`~/.zshrc`)에도 같은 내용을 추가할 수 있습니다.

```bash
export ANDROID_SDK_ROOT="$HOME/Library/Android/sdk"
export PATH="$ANDROID_SDK_ROOT/platform-tools:$ANDROID_SDK_ROOT/emulator:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$PATH"

sdkmanager --licenses
adb version
emulator -version
```

CLI로 Apple Silicon용 시스템 이미지를 설치하려면 다음 명령을 사용할 수 있습니다.

```bash
sdkmanager \
  "platform-tools" \
  "emulator" \
  "platforms;android-35" \
  "build-tools;35.0.0" \
  "system-images;android-35;google_apis;arm64-v8a"

echo no | avdmanager create avd \
  --name MediMate_API_35 \
  --package "system-images;android-35;google_apis;arm64-v8a" \
  --device pixel_7
```

Intel Mac은 `arm64-v8a` 대신 설치 가능한 `x86_64` 이미지를 선택하세요. Android Studio의 `Device Manager`에서 AVD를 만들어도 됩니다.

### 1.3 저장소 받기

```bash
git clone --branch Design/UIDevelop \
  https://github.com/tmdrua02/AX_Hackathon_Caviar.git
cd AX_Hackathon_Caviar
```

Wrapper 실행 권한이 없다면 한 번만 설정합니다.

```bash
chmod +x gradlew backend/gradlew
```

## 2. 환경변수 설정

예시 파일을 복사한 뒤 실제 키를 입력합니다.

```bash
cp .env.example .env
chmod 600 .env
```

필수 항목:

```properties
OPENAI_API_KEY=실제_OpenAI_API_키
OPENAI_CHAT_MODEL=gpt-4o-mini
OPENAI_SUMMARY_MODEL=gpt-4o-mini
OPENAI_TRANSCRIPTION_MODEL=gpt-4o-mini-transcribe

DATA_GO_KR_SERVICE_KEY=공공데이터포털_서비스키
DATA_GO_KR_SERVICE_KEY_ENCODED=true
```

- `OPENAI_API_KEY`: 진료 전사·요약, 메디봇 설명 생성에 사용합니다.
- `DATA_GO_KR_SERVICE_KEY`: 공공 의약품, DUR, 건강기능식품 API에 사용합니다.
- 포털에서 받은 키가 이미 URL 인코딩된 키라면 `DATA_GO_KR_SERVICE_KEY_ENCODED=true`를 유지합니다.
- `.env`, `server/.env`, `backend/.env`, `local.properties`는 Git에서 제외됩니다.
- OpenAI 키와 공공데이터 키를 Android 소스나 APK에 넣지 마세요.

로컬 Spring Boot 서버는 저장소 루트의 `.env`를 자동으로 읽습니다. 셸 환경변수로 같은 이름을 전달하면 해당 값이 우선합니다.

## 3. 로컬 서버 실행

Android 에뮬레이터는 호스트의 `localhost`를 `10.0.2.2`로 접근합니다. 로컬 개발 시 서버 두 개를 먼저 실행하세요.

### 3.1 진료 녹음·메디봇 서버

터미널 1, 저장소 루트에서 실행합니다.

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export GRADLE_USER_HOME="$PWD/.gradle-user-home"
./gradlew :server:bootRun
```

확인:

```bash
curl --fail http://localhost:8080/actuator/health
```

### 3.2 의약품·건강기능식품 서버

터미널 2에서 실행합니다.

```bash
cd backend
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
export GRADLE_USER_HOME="$PWD/../.gradle-user-home"
./gradlew bootRun
```

확인:

```bash
curl --fail http://localhost:8081/health
```

공공데이터 키가 없거나 API 활용 신청이 승인되지 않은 경우 서버 자체는 실행되지만 실제 검색은 설정 또는 공급자 오류를 반환합니다.

## 4. Android 에뮬레이터 실행

진료 녹음을 테스트할 때는 호스트 마이크 입력을 허용하고 콜드 부팅하는 것을 권장합니다.

```bash
export ANDROID_SDK_ROOT="$HOME/Library/Android/sdk"
export PATH="$ANDROID_SDK_ROOT/platform-tools:$ANDROID_SDK_ROOT/emulator:$PATH"

emulator -avd MediMate_API_35 \
  -no-snapshot-load \
  -gpu host \
  -allow-host-audio
```

다른 터미널에서 부팅과 마이크를 확인합니다.

```bash
adb wait-for-device
adb shell 'while [[ "$(getprop sys.boot_completed)" != "1" ]]; do sleep 1; done'
adb emu avd hostmicon
adb devices
```

macOS 권한 알림이 표시되면 마이크 접근을 허용합니다. 입력 파형이 움직이지 않으면 `시스템 설정 > 개인정보 보호 및 보안 > 마이크`에서 Android Emulator 또는 터미널 권한을 확인하세요.

## 5. 컴파일, 테스트, 빌드

### 5.1 Android 테스트와 Debug APK

로컬 서버(`10.0.2.2:8080`, `10.0.2.2:8081`)에 연결되는 기본 Debug APK:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
export GRADLE_USER_HOME="$PWD/.gradle-user-home"

./gradlew :android:app:testDebugUnitTest
./gradlew :android:app:assembleDebug
```

생성 위치:

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

에뮬레이터 설치 및 실행:

```bash
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
adb shell am force-stop com.haneul.medassist
adb shell monkey -p com.haneul.medassist \
  -c android.intent.category.LAUNCHER 1
```

### 5.2 외부 GCE 서버용 Debug APK

`SERVER_URL`은 반드시 마지막 `/`를 포함해야 합니다. 데모 토큰은 저장소에 기록하지 않고 현재 셸에서만 입력합니다.

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
export GRADLE_USER_HOME="$PWD/.gradle-user-home"
export SERVER_URL='http://YOUR_GCE_EXTERNAL_IP/'
read -s 'MEDIMATE_DEMO_TOKEN?Demo API token: '
echo

./gradlew :android:app:assembleDebug \
  -PDEBUG_API_BASE_URL="$SERVER_URL" \
  -PDEBUG_SUPPLEMENT_API_BASE_URL="$SERVER_URL" \
  -PDEBUG_DEMO_API_TOKEN="$MEDIMATE_DEMO_TOKEN"

unset MEDIMATE_DEMO_TOKEN
```

Debug APK에는 데모 API 토큰이 포함되므로 해커톤 시연 외의 실제 인증 방식으로 사용하면 안 됩니다. Release 빌드는 `RELEASE_API_BASE_URL`, `RELEASE_SUPPLEMENT_API_BASE_URL`에 HTTPS 주소가 필요하며 별도의 Android 서명 설정이 필요합니다.

### 5.3 진료 녹음·메디봇 서버

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export GRADLE_USER_HOME="$PWD/.gradle-user-home"

./gradlew :server:test
./gradlew :server:bootJar
```

생성 JAR은 `server/build/libs/`에 있습니다.

### 5.4 공공데이터 백엔드

```bash
cd backend
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
export GRADLE_USER_HOME="$PWD/../.gradle-user-home"

./gradlew test
./gradlew bootJar
```

생성 JAR은 `backend/build/libs/`에 있습니다. 실제 공공 API와 OpenAI를 호출하는 opt-in 테스트는 키와 활용 승인이 준비된 환경에서만 실행합니다.

```bash
./gradlew externalApiTest
./gradlew externalLlmTest
```

### 5.5 전체 로컬 검증

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export GRADLE_USER_HOME="$PWD/.gradle-user-home"
./gradlew :server:test

export JAVA_HOME=$(/usr/libexec/java_home -v 17)
./gradlew :android:app:testDebugUnitTest :android:app:assembleDebug

cd backend
export GRADLE_USER_HOME="$PWD/../.gradle-user-home"
./gradlew test bootJar
```

## 6. Docker로 전체 서버 실행

Docker Desktop을 사용하는 macOS:

```bash
brew install --cask docker
open -a Docker
docker version
docker compose version
```

저장소 루트에서 환경설정과 Compose 구성을 검증한 뒤 실행합니다.

```bash
cp .env.example .env
# .env에 실제 키, DB_PASSWORD, DEMO_API_TOKEN, SERVER_ADDRESS 입력

docker compose -f compose.prod.yml --env-file .env config --quiet
docker compose -f compose.prod.yml --env-file .env build
docker compose -f compose.prod.yml --env-file .env up -d
docker compose -f compose.prod.yml --env-file .env ps
```

로그와 종료:

```bash
docker compose -f compose.prod.yml --env-file .env logs -f --tail=200
docker compose -f compose.prod.yml --env-file .env down
```

`down`은 DB와 녹음 볼륨을 삭제하지 않습니다. 데이터 보존을 위해 `down -v`는 사용하지 마세요.

## 7. Ubuntu/GCE 클린 인스턴스 배포

최소 권장 사양은 Ubuntu 22.04/24.04, 2 vCPU, RAM 4GB, 디스크 20GB입니다. 아래 설치 명령에는 `sudo` 권한이 필요합니다. GCE OS Login 사용자가 `sudo` 권한이 없다면 인스턴스 관리자에게 OS Login Admin 권한을 요청하세요.

### 7.1 Git과 Docker Engine 설치

```bash
sudo apt-get update
sudo apt-get install -y ca-certificates curl git

sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
  -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc

echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io \
  docker-buildx-plugin docker-compose-plugin
sudo systemctl enable --now docker containerd
sudo usermod -aG docker "$USER"
```

그룹 변경 후 SSH에서 로그아웃하고 다시 접속한 뒤 확인합니다.

```bash
docker version
docker compose version
```

### 7.2 소스와 환경변수 준비

```bash
git clone --branch Design/UIDevelop \
  https://github.com/tmdrua02/AX_Hackathon_Caviar.git
cd AX_Hackathon_Caviar
cp .env.example .env
chmod 600 .env
nano .env
```

`.env`에서 최소한 다음 값을 설정합니다.

- `SERVER_ADDRESS=http://고정_외부_IP` 또는 실제 HTTPS 도메인
- `DEMO_API_TOKEN`: `openssl rand -hex 32`로 생성
- `DB_PASSWORD`: `openssl rand -hex 32`로 별도 생성
- `OPENAI_API_KEY`
- `DATA_GO_KR_SERVICE_KEY`

### 7.3 빌드와 실행

```bash
docker compose -f compose.prod.yml --env-file .env config --quiet
docker compose -f compose.prod.yml --env-file .env build
docker compose -f compose.prod.yml --env-file .env up -d
docker compose -f compose.prod.yml --env-file .env ps
```

외부에서 확인합니다.

```bash
curl --fail http://YOUR_GCE_EXTERNAL_IP/actuator/health
curl --fail http://YOUR_GCE_EXTERNAL_IP/health
curl -i \
  -H 'X-Demo-Api-Key: YOUR_DEMO_API_TOKEN' \
  http://YOUR_GCE_EXTERNAL_IP/api/v1/home
```

GCE 방화벽에는 TCP `80`이 허용되어야 합니다. 도메인과 HTTPS를 사용하면 TCP `443`도 허용하고 `.env`의 `SERVER_ADDRESS`를 도메인으로 변경하세요. Caddy가 TLS 인증서 발급과 갱신을 담당합니다.

### 7.4 서버 업데이트

```bash
cd ~/AX_Hackathon_Caviar
git pull --ff-only
docker compose -f compose.prod.yml --env-file .env build
docker compose -f compose.prod.yml --env-file .env up -d
docker compose -f compose.prod.yml --env-file .env ps
```

터미널 또는 SSH 창을 닫아도 Docker 컨테이너는 `restart: unless-stopped` 정책으로 계속 실행됩니다. VM 자체는 켜져 있어야 합니다.

## 8. 자주 발생하는 문제

### `adb: command not found`

```bash
export ANDROID_SDK_ROOT="$HOME/Library/Android/sdk"
export PATH="$ANDROID_SDK_ROOT/platform-tools:$PATH"
adb devices
```

### Gradle 오류에 JDK 버전만 표시되는 경우

Android와 `backend`는 JDK 17, `server`는 JDK 21을 사용했는지 확인합니다.

```bash
echo "$JAVA_HOME"
java -version
```

### 에뮬레이터 마이크 입력이 없는 경우

- AVD를 `-allow-host-audio -no-snapshot-load`로 다시 실행합니다.
- `adb emu avd hostmicon`을 실행합니다.
- macOS 마이크 개인정보 보호 권한을 확인합니다.
- 다른 녹음 앱이나 통화 앱을 종료합니다.

### Docker Compose가 환경변수 누락을 보고하는 경우

현재 위치와 `.env`의 실제 값을 확인합니다. 값 자체를 출력하지 않는 다음 검사를 권장합니다.

```bash
pwd
test -s .env
grep -E '^(OPENAI_API_KEY|DATA_GO_KR_SERVICE_KEY|DEMO_API_TOKEN|DB_PASSWORD)=' .env \
  | sed 's/=.*/=<set>/'
docker compose -f compose.prod.yml --env-file .env config --quiet
```

## 9. 생성 파일과 Git 정책

다음 파일은 빌드 또는 로컬 환경에서 자동 생성되므로 Git에 커밋하지 않습니다.

- `.gradle/`, `.gradle-user-home/`, 모든 모듈의 `build/`
- `dist/`, `node_modules/`, `.next/`, `.wrangler/`
- `*.apk`, `*.aab`, `*.class`
- `local.properties`
- `.env`, `server/.env`, `backend/.env`
- 업로드 파일, 로컬 DB, IDE/OS 임시 파일

생성 파일을 삭제해도 소스는 영향을 받지 않으며 다음 빌드에서 다시 만들어집니다.

```bash
./gradlew clean
(cd backend && ./gradlew clean)
rm -rf medication-safety-wireframe/dist
```

## 10. 보안 주의사항

- OpenAI 및 공공데이터 키는 서버 환경변수로만 전달합니다.
- `.env`는 절대 Git에 추가하지 않습니다.
- 이미 노출된 키와 데모 토큰은 폐기 후 재발급합니다.
- Debug APK의 데모 토큰은 해커톤 시연용 접근 제어일 뿐 사용자 인증이 아닙니다.
- 의료 AI 결과는 참고용이며 실제 처방과 의료진의 판단을 우선합니다.
