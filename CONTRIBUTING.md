# 같이 작업하는 방법

**`main` 브랜치에 바로 올리지 않습니다.** 작업할 때마다 브랜치를 만들고, Pull Request(PR)로 합칩니다. 서로의 작업을 덮어쓰지 않고, 합치기 전에 한 번 더 확인하기 위해서입니다.

## 한눈에 보기

1. 최신 `main`을 받는다
2. 내 작업용 **브랜치**를 만든다
3. 작업하고 **커밋**한다
4. 브랜치를 GitHub에 올린다(push)
5. GitHub에서 **Pull Request**를 연다
6. 팀원 **1명 이상**이 확인(Approve)한다
7. 합친다(Merge) 후 브랜치를 지운다

## 명령어로 하는 방법

```bash
# 1. 최신 main 받기
git checkout main
git pull

# 2. 작업용 브랜치 만들기 (이름 규칙은 아래 참고)
git checkout -b feature/story-ui

# 3. 작업 후 커밋 (파일을 하나씩 고르는 것을 권장)
git add WindowStory/app/src/main/java/com/changbak/story/ui/SceneView.kt
git commit -m "도착 화면에 깃발 추가"

# 4. 내 브랜치를 GitHub에 올리기
git push -u origin feature/story-ui
```

그다음 GitHub 저장소 페이지에 나타나는 **Compare & pull request** 버튼을 눌러 PR을 엽니다. 양식(체크 목록)을 채우고, 오른쪽 **Reviewers**에서 팀원을 고릅니다.

**GitHub Desktop**을 쓰면: 위쪽 *Current Branch* → *New Branch*로 브랜치를 만들고, 작업 후 *Commit to ...* → *Push origin* → *Create Pull Request* 순서로 하면 같습니다.

## 브랜치 이름

`종류/짧은-설명` 형태로 영어 소문자와 하이픈을 씁니다.

| 종류 | 언제 | 예시 |
|---|---|---|
| `feature/` | 새 기능, 화면 | `feature/story-ui`, `feature/voice-quest` |
| `fix/` | 버그 수정 | `fix/belt-pause` |
| `docs/` | 기획서, README, 메모 | `docs/planning-v4` |
| `content/` | 이야기 문장, 음원 교체 | `content/suno-theme` |

## 커밋 메시지

무엇을 바꿨는지 한 줄로 씁니다. 한국어로 써도 됩니다.

- 좋은 예: `벨트를 풀면 이야기가 멈추도록 수정`, `음악 취향 화면에 악기 선택 추가`
- 나쁜 예: `수정`, `ㅇㅇ`, `최종_진짜최종`

## Pull Request 규칙

- 한 PR에는 **한 가지 일**만 담습니다. 작을수록 확인하기 쉽습니다.
- **팀원 1명 이상**이 확인한 뒤에 합칩니다. 내가 연 PR을 내가 바로 합치지 않습니다.
- 합치기 전에 Android Studio에서 **빌드되고 실행되는지** 확인합니다.
- 화면이 바뀌었다면 **스크린샷**을 PR에 붙입니다.
- 합친 뒤에는 브랜치를 지웁니다(GitHub의 *Delete branch* 버튼).
- 작업을 시작하기 전에 `main`을 다시 받아 최신 상태로 시작합니다.

## 충돌을 줄이는 방법

같은 파일을 두 사람이 동시에 고치면 합칠 때 충돌이 납니다.

- 시작하기 전에 팀 채팅방에 "나는 `SceneView.kt` 고칠게"처럼 **어떤 파일을 고치는지 알립니다.**
- 큰 파일(`MainActivity.kt`)은 가능하면 한 번에 한 명만 고칩니다.
- 오래 걸리는 작업은 중간중간 `main`을 내 브랜치에 합쳐 둡니다.

```bash
git checkout main && git pull
git checkout feature/story-ui
git merge main
```

충돌이 나면 혼자 고민하지 말고 팀원에게 먼저 물어봅니다.

## 절대 올리지 않는 것

- **개인정보**: 이름, 휴대폰 번호, 이메일, 서명이 채워진 참가 신청서. 이런 파일은 팀 안에서 따로 공유합니다. 파일 이름에 `제출`이나 `최종`이 들어가면 자동으로 올라가지 않습니다.
- **비밀 정보**: API 키, 비밀번호, 서명 키 파일(`*.jks`, `*.keystore`)
- **빌드 결과물**: `build/`, `*.apk`, `.gradle/`, `.idea/`, `local.properties`

실수로 올렸다면 PR을 합치기 전이면 바로 닫고 알려 주세요. 이미 합쳤다면 기록에 남으므로 **혼자 지우려고 하지 말고** 바로 팀에 알립니다.

## 음원(Suno)

Suno로 만든 곡은 **유료 요금제 계정**으로 생성한 것만 쓸 수 있습니다. 곡을 바꿀 때는 `WindowStory/app/src/main/res/raw/`의 같은 파일 이름(`theme_bright.wav`, `theme_calm.wav`, `theme_adventure.wav`)으로 교체하고 `content/` 브랜치로 PR을 엽니다. 용량이 큰 파일(수십 MB)은 먼저 팀에 알려 주세요.
