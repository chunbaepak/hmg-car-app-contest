# 현대자동차그룹 차량용 앱 개발 공모전: 창밖 이야기

경로와 남은 시간에 맞춰 동화가 진행되고, 목적지에 도착하면 이야기도 함께 끝나는 **뒷좌석 아이용 오디오 동화 앱**입니다. Pleos Connect(Android Automotive OS) 앱으로 개발합니다.

- 공식 안내: https://appdevchallenge.auto-hmg.io/
- 1차 접수 마감: 2026-11-15 23:59 / 2차 PT 심사: 2026-12-03~04 / 최종 발표: 2026-12-18

## 폴더 구성

| 폴더·파일 | 내용 |
|---|---|
| `WindowStory/` | **본 앱.** 실행 방법, 구조, 확인된 것과 아직 안 된 것은 [WindowStory/README.md](WindowStory/README.md) 참고 |
| `DoorHello/` | Vehicle SDK 연습 예제(문·트렁크 위치 읽기와 변경). 본 앱과는 별개 |
| `기획서[창밖이야기]_수정본.docx` | 참가 신청서 양식에 기획 내용을 채운 기획서(수정본). 이름·연락처·서명은 비어 있음 |
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

**`main`에 바로 올리지 않습니다. 작업할 때마다 브랜치를 만들고 Pull Request(PR)로 합칩니다.**

```bash
git checkout main && git pull        # 최신 main 받기
git checkout -b feature/짧은-설명     # 내 작업용 브랜치 만들기
# ... 작업하고 커밋 ...
git push -u origin feature/짧은-설명  # GitHub에 올리고, 저장소 페이지에서 PR 열기
```

- PR은 **팀원 1명 이상이 확인**한 뒤에 합칩니다. 내가 연 PR을 내가 바로 합치지 않습니다.
- 한 PR에는 한 가지 일만 담고, 합치기 전에 빌드와 에뮬레이터 실행을 확인합니다.
- 같은 파일을 동시에 고치지 않도록, 작업을 시작할 때 팀 채팅방에 어떤 파일을 고치는지 알립니다.
- **개인정보를 올리지 않습니다.** 이름, 연락처, 서명이 채워진 참가 신청서는 팀 안에서 따로 공유합니다. 파일 이름에 `제출`이나 `최종`이 들어가면 자동으로 올라가지 않습니다.
- API 키, 계정 정보, 서명 키 파일을 코드에 넣지 않습니다.
- Suno로 만든 곡은 유료 요금제 계정으로 생성한 것만 씁니다.

자세한 방법(브랜치 이름, 커밋 메시지, 충돌 해결, GitHub Desktop 사용법)은 **[CONTRIBUTING.md](CONTRIBUTING.md)** 를 봐 주세요.
