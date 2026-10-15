# where-mock-location

GPS 위치를 가짜로 주입해서 위치 기반 기능을 개발 중에 테스트하기 위한 개인용 개발 도구.
두 개의 독립 앱으로 구성됨:

- **Android 앱** — 실제 GPS 위조를 수행. 안드로이드 공식 mock-location-provider API 사용
  (루팅 불필요, 개발자 옵션에서 "모의 위치 앱 선택" 필요). 지도에서 직접 위치를 찍거나,
  주소 검색, 좌표 수동 입력, 즐겨찾기 프리셋으로 사용 가능. 단독으로도 완전히 동작함.
- **macOS 앱** — 메뉴바에 상주하는 원격 컨트롤러. Mac은 GPS 하드웨어가 없어서 자체 위치는
  조작 못 하고, 대신 지도에서 위치를 찍으면 같은 Wi-Fi에 있는 폰으로 실시간 전송함.

## 구조

```
android/   Android 앱 (Kotlin, Gradle)        — android-app 브랜치
mac/       macOS 앱 (Swift, SwiftPM)          — macos-app 브랜치
docs/      설계 문서, 구현 플랜
```

각 앱의 빌드/설치/페어링 방법은 해당 폴더의 README 참고:
[android/README.md](android/README.md) · [mac/README.md](mac/README.md)

## 통신 프로토콜

Mac과 폰은 로컬 네트워크 WebSocket으로 통신함. 폰 앱이 서버 역할을 하고, 페어링 토큰으로
인증되지 않은 좌표 주입을 막음.

```
Mac -> Phone: { "token": "<페어링 토큰>", "lat": 37.5665, "lng": 126.9780 }
Phone -> Mac: { "status": "ok" | "error", "message": "..." }
```

## 보안 경계

mock-location-provider 방식은 안드로이드가 주입된 위치에 `isFromMockProvider()` 플래그를
자동으로 표시함. 이 플래그를 체크하는 앱(위치 인증, 부정거래 탐지 등)은 정상적으로 탐지함 —
이건 의도된 OS 레벨 보호 장치이고, 이 프로젝트는 이걸 우회하지 않음.

## 상태

개발 중. 두 앱 다 개별 PR로 작업 중 — [#1 Android](https://github.com/tpgusgh/where-mock-location/pull/1),
[#2 macOS](https://github.com/tpgusgh/where-mock-location/pull/2).
