# 「창밖 이야기」 개발 메모: SDK 조사 결과

2026-11-15 기준. 공식 문서(document.pleos.ai)와 SDK 파일을 직접 열어 확인한 내용이다. 앱 코드는 아직 작성하지 않았다.

## 의존성 (Maven: https://nexus-playground.pleos.ai/repository/maven-releases/)

| SDK | 좌표 |
|---|---|
| Vehicle | `ai.pleos.playground:Vehicle:2.3.2` |
| NaviHelper | `ai.pleos.playground:NaviHelper:3.0.5` |
| TextToSpeech | `ai.pleos.playground:TextToSpeech:3.3.1` |
| SpeechToText | `ai.pleos.playground:SpeechToText:3.3.1` |
| FusedLocation | `ai.pleos.playground:FusedLocation:1.1.3` |

## 매니페스트 권한

- 좌석·안전벨트: `pleos.car.permission.CAR_SEATS` (기어 권한 이름은 미확인. API의 `@RequiresPermission` 확인 필요)
- 내비 정보 조회: `pleos.car.permission.NAVI_ROUTE` (경로 요청까지 하려면 `NAVI_ROUTE_SEARCH`)
- 낭독: `pleos.car.permission.TTS_SERVICE`
- 음성 인식: `pleos.car.permission.STT_SERVICE`
- 위치: `pleos.car.permission.FUSED_LOCATION` (보호 수준 dangerous)

## SDK별 사용법 요약

### TextToSpeech
- `TextToSpeech(context, Mode.ON_DEVICE)`: 차량 내장 엔진. 네트워크와 앱 등록(Client ID/Secret)이 필요 없다. HYBRID·SERVER 모드는 Pleos Playground의 My Project 자격 증명으로 `registerApp()`을 해야 한다.
- 순서: `initialize(OnInitializeListener)` → `addEventListener(EventListener)` → `speak(text, isFinal)` → `stop()` → `release()`
- `EventListener`: onStart, onReady, onDone, onError, onStop, onUpdatedRms. 장면 낭독이 끝난 시점은 `onDone`으로 받는다.

### SpeechToText
- `SpeechToText(context, Mode.ON_DEVICE)`: 앱 등록 불필요.
- 순서: `initialize()` → `addListener(ResultListener)` → `request()`(마이크 시작) → `stop()`
- `ResultListener.onUpdated(stt, completed)`로 인식 문장을 받는다.
- ON_DEVICE 모드에서는 `sendAudio(pcm)` + `completeAudioSend()`로 녹음 파일(16kHz, 16bit, mono PCM)을 넣어 테스트할 수 있다. 에뮬레이터 마이크 없이 시연 검증이 가능하다.

### NaviHelper
- `NaviHelper(context).initialize(onConnected, onFailed)` → `addListener(NaviHelperEventListener)`
- 쓸 이벤트: `onRouteStarted(RouteStartInfo: duration, distance, name)`, `onDrivingInfo(DrivingInfo: destination.duration/distance, waypoints)`, `onCurrentLocationInfo(latitude, longitude, address)`, `onWayPointArrived`, `onDestinationArrived`, `onRouteCancelled`
- 조회 요청: `getRouteStateInfo()`, `getCurrentLocationInfo()`, `getDestinationInfo()`
- 문서에 "Navigation 앱의 구현 시나리오에 따라 전달되는 이벤트가 달라질 수 있다"고 적혀 있다. 에뮬레이터에서 실제로 이벤트가 오는지는 아직 확인하지 못했다.

### Vehicle
- `Vehicle(context).initialize()` → `getSeat()`, `getGear()`
- Seat: `getCurrentSeatOccupancy`, `registerSeatOccupancy`, `getCurrentSeatBeltBuckledState`, `registerSeatBeltBuckledState`
- 버추얼 콘솔(http://127.0.0.1:8088, 먼저 `adb forward tcp:8088 tcp:8088`)의 Convenience > Seat Occupancy, Safety > Telltales, Motion > Gear Selection으로 값을 바꿔 검증한다.
- 주의: 에뮬레이터는 앱에서 보낸 제어 요청 중 일부만 반영한다(문 위치는 트렁크만). 이 앱은 조회와 알림만 쓰므로 영향이 없다.

### FusedLocation
- `registerFusedLocationCallback(FusedLocationListener)` → `onChanged(LocationData?)`
- 에뮬레이터 기본 위치는 미국 마운틴뷰(37.42, -122.08)다.

## 설계 방향 (아직 구현 전)

- 이동 정보 공급원을 둘로 나눈다: 실제 내비(NaviHelper)와 시연용 경로 재생 모드. 이야기 엔진은 진행률, 남은 시간, 장소 이벤트만 받는다.
- 낭독은 TTS ON_DEVICE, 음성 질문은 STT ON_DEVICE. 시연용으로 "얼마나 남았어?" 버튼도 둔다.
- 테마곡은 Suno로 미리 만든 파일을 `res/raw`에 넣는다. Suno 곡은 사용자가 유료 계정으로 직접 생성해야 하므로, 그 전까지는 임시 음원을 쓴다.
- 화면은 주행 중 조작이 없게 하고, 취향 설정은 P단에서만 연다.

## 남은 일

1. 프로젝트 골격 만들기 (DoorHello와 같은 Gradle 9.9.1, AGP 9.5.1 구성)
2. 이야기 엔진과 이야기 1편(4장)
3. 차량·내비 연동, 시연용 경로 모드
4. 낭독, 음성 인식, 테마곡 재생
5. 화면
6. 빌드와 에뮬레이터 검증 (NaviHelper 이벤트, TTS ON_DEVICE 동작 여부 확인 포함)

## 개발하면서 확인한 것 (2026-11-15 추가)

- **TTS·STT 언어는 `Locale.KOREA`(ko-KR)로 설정해야 한다.** `Locale.KOREAN`(ko)을 주면 내장 엔진이 `unsupported locale: ko-` 오류를 내고 `ON_DEVICE_ENGINE_ERROR`가 된다.
- **기어 권한은 `pleos.car.permission.CAR_DRIVE`다.** 기어 API는 deprecated로 표시된다.
- **위치 권한 요청 창은 정차(P) 중에만 띄운다.** 주행 상태에서 띄우면 "주행 중에는 사용할 수 없습니다" 화면이 된다.
- **기어 D에서는 직접 설치한 앱 화면이 가려진다.** `distractionOptimized` 선언과 무관했다. 원인 미확정. 시연은 기어 P에서 한다.
- **에뮬레이터의 뒷좌석 벨트 신호는 항상 false다.** 버추얼 콘솔에는 좌석 탑승(Seat Occupancy)만 있고 좌석별 벨트 항목이 없다. Telltales의 Seat Belt는 이 값에 영향을 주지 않았다.
- **NaviHelper 서비스는 연결된다.** 경로 이벤트 수신은 미확인.
- 앱 코드는 `WindowStory/` 폴더에 있다. 실행 방법과 남은 문제는 `WindowStory/README.md` 참고.
