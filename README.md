# Limbus Archive — Android

`dist/LimbusArchive.apk` 를 폰에 설치하세요 ("출처를 알 수 없는 앱 설치" 허용 필요). Android 7.0 이상.

앱을 열면 두 가지 모드 중 하나를 고릅니다.

## 1. PC 연결 (전체 기능)
PC에서 실행 중인 데스크톱 앱(LimbusArchive.exe)에 폰이 붙어서, **PC에서만 되던 기능을 전부** 폰 화면으로 씁니다
(설정, 파일 탐색, 스냅샷, 스프라이트 작업실, Duel / Auto Battler / Online room / Mirror, 애니메이션·FX 렌더링, 모드 등).

처음 한 번:
1. PC에서 `LimbusArchive.exe` 실행 (사용하는 동안 켜 두기)
2. PC에서 `pc/enable-mobile.bat` 실행 → 관리자 권한 "예" → 창에 PC IP가 표시됨 (재부팅 후에도 유지)
3. 폰 앱에서 그 IP 입력 → **연결** (또는 **자동 찾기**). 폰과 PC는 같은 Wi‑Fi.
   - 밖에서 쓰려면 PC·폰에 Tailscale 설치 후 Tailscale IP(100.x.x.x) 입력
4. 끄려면 `pc/disable-mobile.bat`

동작 방식: 데스크톱 앱 서버는 `127.0.0.1:47821`에서 Host 헤더가 자기 주소인 요청만 받습니다.
- PC: Windows 기본 `netsh portproxy`로 47822 → 127.0.0.1:47821 전달. 방화벽은 같은 네트워크(localsubnet)와 Tailscale 대역만 허용합니다.
- 폰: 앱 내부 중계기가 폰의 `127.0.0.1:47821` → `PC:47822`로 바이트를 그대로 넘깁니다. WebView는 `http://127.0.0.1:47821`을 열기 때문에 서버가 로컬 접속으로 인식합니다.

## 2. 웹 버전
PC 없이 https://limbus-archive.com 을 엽니다 (공개된 페이지만).

## 공통
- 뒤로가기 = 페이지 뒤로 (첫 페이지에서는 모드 선택 화면으로), 동영상 전체 화면
- 다운로드 → `Download/LimbusArchive/`, 파일 업로드 지원
- `limbusarchive://` 링크 지원

## 왜 폰 단독 실행이 아닌가
원본은 PyInstaller로 묶은 Python 서버 + Unity(IL2CPP) 뷰어입니다. 핵심 모듈(viewer, versus_engine, story, collection 등)은
소스 없는 Windows 전용 바이너리(.pyd)이고, Unity 뷰어도 프로젝트 소스가 없으며, Steam판 게임 파일을 읽습니다.
그래서 이 기능들은 PC에서 계산하고 폰은 화면 역할만 합니다.

## 빌드
```
cd android
./gradlew assembleRelease   # Android SDK 35 (local.properties 의 sdk.dir)
```
서명 키: `android/limbusarchive.keystore` (비밀번호 `limbusarchive`, 개인 설치용).
