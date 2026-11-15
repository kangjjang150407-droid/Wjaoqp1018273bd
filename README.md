# Limbus Archive — Android

`dist/LimbusArchive.apk` 를 휴대폰에 옮겨 설치하세요 (설정에서 "출처를 알 수 없는 앱 설치" 허용 필요).

## 구성
원본(Windows용 LimbusArchive.zip)은 PyInstaller로 묶인 Python 로컬 서버 + 웹 UI + Unity 뷰어(LimbusViewer, IL2CPP)입니다.
Python 서버는 PC에 설치된 Limbus Company(Steam) 게임 파일을 읽고, 핵심 모듈 일부가 Windows 전용 바이너리(.pyd)라
안드로이드에서 그대로 실행할 수 없습니다. 그래서 같은 UI의 웹 버전(https://limbus-archive.com)을
전체 화면 WebView로 감싼 네이티브 앱으로 재구성했습니다.

- 뒤로가기 = 페이지 뒤로, 동영상 전체 화면 지원
- 파일 다운로드(스프라이트/이미지 저장 등) → `Download/LimbusArchive/`
- 파일 업로드(파일 선택기) 지원
- `limbusarchive://` 링크 지원 (데스크톱 앱과 동일)
- 외부 링크는 브라우저/해당 앱으로 열림
- 인터넷 연결 필요

## 빌드
```
cd android
./gradlew assembleRelease   # Android SDK 35 필요 (local.properties 의 sdk.dir)
```
결과물: `android/app/build/outputs/apk/release/app-release.apk`
서명 키: `android/limbusarchive.keystore` (비밀번호 `limbusarchive`, 개인 설치용). 업데이트 설치 시 같은 키로 서명해야 합니다.
