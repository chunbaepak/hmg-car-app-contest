# 현대자동차그룹 차량용 앱 개발 공모전: 창밖 이야기

경로와 남은 시간에 맞춰 동화가 진행되고, 목적지에 도착하면 이야기도 함께 끝나는 **뒷좌석 아이용 오디오 동화 앱**입니다. Pleos Connect(Android Automotive OS) 앱으로 개발합니다.

- 공식 안내: https://appdevchallenge.auto-hmg.io/
- 1차 접수 마감: 2026-11-15 23:59 / 2차 PT 심사: 2026-12-03~04 / 최종 발표: 2026-12-18

## 폴더 구성

| 폴더·파일 | 내용 |
|---|---|
| `WindowStory/` | **본 앱.** 실행 방법, 구조, 확인된 것과 아직 안 된 것은 [WindowStory/README.md](WindowStory/README.md) 참고 |
| `DoorHello/` | Vehicle SDK 연습 예제(문·트렁크 위치 읽기와 변경). 본 앱과는 별개 |
| `기획서[창밖이야기]_초안.docx` | 참가 신청서 양식에 기획 내용을 채운 초안 |
| `개발메모_SDK조사.md` | SDK별 사용법, 권한, 에뮬레이터에서 확인한 것 |

## 개발 환경 준비

1. [Android Studio](https://developer.android.com/studio) 설치
2. Android SDK Manager에서 **Update Site 추가** (Pleos 에뮬레이터 이미지용)
   - Mac(Apple 칩): `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-arm64-sys-img.xml`
   - Windows/Linux: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml`
3. SDK Platforms에서 Android API 34의 **Pleos Connect System Image** 설치 (약 4.7GB, 42dot 라이선스 동의 필요)
4. Device Manager에서 Automotive 형태의 **Pleos Connect v2.1.0** 기기를 만들고, Boot option을 **Cold boot**, RAM을 4GB로 설정
5. 프로젝트(`WindowStory`)를 Android Studio로 열고 ▶︎ 실행

자세한 절차는 [Pleos Connect Emulator 설치 및 실행](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator) 문서를 따릅니다. `local.properties`는 각자 PC의 SDK 경로가 들어가므로 저장소에 없습니다. Android Studio가 자동으로 만들어 줍니다.

## 팀 작업 규칙

- **개인정보를 올리지 않습니다.** 참가 신청서의 이름, 연락처, 서명, 이메일이 채워진 파일은 저장소에 올리지 않고 팀 안에서 따로 공유합니다. 이 저장소의 `.gitignore`는 `기획서*제출*`, `*최종*.docx`, `*.pdf`를 막아 둡니다.
- API 키, 계정 정보, 서명 키 파일을 코드에 넣지 않습니다.
- Suno로 만든 곡은 유료 요금제 계정으로 생성해야 상업적 이용이 가능합니다. 곡을 `WindowStory/app/src/main/res/raw/`에 같은 파일 이름으로 바꿔 넣습니다.
- 작업은 브랜치를 만들어 진행하고, 합칠 때는 Pull Request로 서로 확인합니다.
