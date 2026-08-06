# 하늘약손 · 메디봇

진료 음성을 녹음하고 OpenAI로 전사·화자 구분·구조화 요약을 생성하며, 복약 질문을 메디봇에 물어볼 수 있는 Android/Spring Boot 앱입니다.

## 준비 사항

- JDK 21
- Android SDK 35 및 실행 중인 Android 에뮬레이터
- OpenAI API 키

Gradle Wrapper가 서버와 Android 의존성을 자동으로 내려받으므로 별도의 패키지 설치 명령은 없습니다.

## OpenAI 설정

저장소 루트에서 예시 파일을 복사합니다.

```bash
cp .env.example .env
```

생성된 `.env`의 빈 값에 키를 넣습니다.

```properties
OPENAI_API_KEY=발급받은_실제_API_키
OPENAI_CHAT_MODEL=gpt-4o-mini
OPENAI_SUMMARY_MODEL=gpt-4o-mini
OPENAI_TRANSCRIPTION_MODEL=gpt-4o-mini-transcribe
```

`.env`와 `server/.env`는 Git에서 제외됩니다. API 키는 Android 앱에 포함되지 않고 로컬 Spring Boot 서버에서만 읽습니다. 셸 환경변수 `OPENAI_API_KEY`로 직접 전달해도 됩니다.

## 실행

저장소 루트에서 서버를 시작합니다.

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) GRADLE_USER_HOME="$PWD/.gradle-user-home" ./gradlew :server:bootRun
```

다른 터미널에서 호스트 마이크 입력을 허용해 Android 에뮬레이터를 실행합니다. 이 옵션이 없으면 최신 Emulator가 마이크 입력을 0으로 만들 수 있습니다.

```bash
/Volumes/tmdruaSSD/AndroidStudio/emulator/emulator \
  @Medium_Phone \
  -no-snapshot-load \
  -gpu host \
  -allow-host-audio
```

`-gpu host`는 Apple Silicon에서 소프트웨어 렌더러의 CPU 경합으로 마이크 공급이 멈추는 현상을 줄입니다. 에뮬레이터가 부팅된 뒤 호스트 마이크를 명시적으로 다시 켭니다.

```bash
/Volumes/tmdruaSSD/AndroidStudio/platform-tools/adb wait-for-device
/Volumes/tmdruaSSD/AndroidStudio/platform-tools/adb emu avd hostmicon
```

macOS에서 마이크 접근 알림이 나타나면 허용해야 합니다. 입력 파형이 계속 바닥에 붙어 있으면 `시스템 설정 > 개인정보 보호 및 보안 > 마이크`에서 Android Emulator 또는 실행한 터미널의 권한도 확인합니다.

에뮬레이터가 부팅되면 앱을 설치합니다.

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) GRADLE_USER_HOME="$PWD/.gradle-user-home" ./gradlew :android:app:installDebug
```

에뮬레이터의 앱은 기본적으로 호스트 서버 `http://10.0.2.2:8080/`에 연결합니다.

## 검증

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) GRADLE_USER_HOME="$PWD/.gradle-user-home" ./gradlew :server:test :android:app:testDebugUnitTest :android:app:assembleDebug
```

생성되는 APK는 `android/app/build/outputs/apk/debug/app-debug.apk`입니다.
