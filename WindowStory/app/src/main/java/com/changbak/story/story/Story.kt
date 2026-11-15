package com.changbak.story.story

/** 이야기 한 장. lines는 한 번에 낭독하는 단위다. */
data class Chapter(
    val title: String,
    val lines: List<String>,
    /** 이 장의 보물을 찾는 문장의 순서(0부터). 이 문장을 읽고 나면 보물 하나를 찾은 것으로 센다. */
    val treasureLine: Int,
    /** 창밖을 보게 하는 과제. 없으면 null. */
    val quest: String? = null,
    /** 장이 시작될 때 나는 효과음. */
    val effect: Effect = Effect.CHIME,
)

/** 이야기에 장면으로 끼어드는 장소 종류. */
enum class PlaceType { RIVER, TUNNEL, SEA }

/**
 * 이야기 한 편.
 * {dest}는 목적지 이름, {name}은 경유지 이름, {n}은 보물(장) 수로 바뀐다.
 */
data class Story(
    val title: String,
    val greeting: String,
    val tripIntro: String,
    val chapters: List<Chapter>,
    val ending: String,
    val placeScenes: Map<PlaceType, String>,
    /** 경유지(휴게소)에 도착했을 때. 이 장면 뒤에는 다시 출발할 때까지 이야기를 멈춘다. */
    val restScene: String,
    val restResume: String,
    val beltPause: String,
    val beltResume: String,
    /** 벨트를 계속 매지 않을 때 운전자에게 하는 말. */
    val driverBeltAlert: String,
    val questDone: String,
    /** 다음 장까지 시간이 남을 때(길이 막힐 때 등) 끼워 넣는 추가 과제. */
    val bonusQuests: List<String>,    /** "얼마나 남았어?"의 대답. {count}는 남은 보물 수("두 개"), {time}은 남은 시간이다. */
    val remainingAnswer: String,
    /** 보물을 모두 찾은 뒤의 대답. */
    val remainingAnswerLast: String,
)

object StoryRepository {

    /**
     * 내장 이야기 1편(4장). 바다로 가는 길에 맞춘 이야기이며, 네트워크 없이도 진행된다.
     * 아이가 주인공(탐험 대장)이고, 여우 루루는 길잡이다. 장마다 바다 보물을 하나씩 찾는다.
     */
    val seaKingdom = Story(
        title = "바다 왕국의 잃어버린 보물",
        greeting = "안전벨트 착용 완료! 바다 왕국으로 떠날 준비가 됐구나! " +
            "나는 길잡이 여우 루루야. 차가 출발하면 모험을 시작하자.",
        tripIntro = "자, 출발! 오늘 우리는 {dest}까지 가. 그곳에는 바다 왕국이 숨어 있대. " +
            "길 위에 흩어진 바다 보물 {n}개를 모두 찾으면 왕국의 문이 열려. 오늘의 탐험 대장은 바로 너야!",
        chapters = listOf(
            Chapter(
                title = "출발! 바다 보물을 찾아서",
                lines = listOf(
                    "옛날 옛날, 바다 왕국에는 반짝이는 별 모양 보물이 네 개 있었어. 그런데 큰 파도가 치던 밤, 보물이 모두 육지로 날아가 버렸지.",
                    "보물이 없으면 바다 왕국의 문이 열리지 않아. 그래서 왕국의 길잡이인 나, 루루가 용감한 탐험 대장을 찾아왔어.",
                    "어, 네 자리 옆에서 뭔가 반짝이는걸? 첫 번째 바다 보물이야! 출발하자마자 하나를 찾았어.",
                ),
                treasureLine = 2,
                quest = "다음 보물은 빨간 것을 좋아한대. 창밖에서 빨간 트럭이나 빨간 자동차를 찾아 줘. " +
                    "찾으면, 찾았어! 하고 말해 줘.",
            ),
            Chapter(
                title = "숲속의 반짝임",
                lines = listOf(
                    "길은 초록 숲으로 이어졌어. 나무들이 손을 흔들며 탐험 대장을 반겨 줬지.",
                    "숲속 다람쥐가 달려와 말했어. 반짝이는 걸 봤어! 커다란 나무 아래를 봐.",
                    "나무 아래에는 두 번째 바다 보물이 잎사귀에 싸여 있었어. 이제 보물이 두 개야!",
                ),
                treasureLine = 2,
                quest = "다람쥐가 고맙다고 놀이를 하재. 창밖 하늘에서 재미있는 모양의 구름을 찾아봐. " +
                    "찾으면, 찾았어! 하고 말해 줘.",
            ),
            Chapter(
                title = "바람 부는 언덕 너머",
                lines = listOf(
                    "숲을 지나자 높은 언덕이 나타났어. 바람이 휘잉 불어서 루루의 꼬리가 깃발처럼 펄럭였지.",
                    "언덕 꼭대기 바위 틈에서 세 번째 바다 보물이 반짝였어. 바람이 길을 알려 준 거야.",
                    "언덕 너머에서 짭짤한 바다 냄새가 나기 시작했어. 바다 왕국이 가까워지고 있어.",
                ),
                treasureLine = 1,
            ),
            Chapter(
                title = "바다 왕국의 문",
                lines = listOf(
                    "드디어 마지막 길이야. 갈매기들이 하늘에서 길을 안내해 줬어.",
                    "모래밭 위 조개껍데기 속에 네 번째 바다 보물이 숨어 있었어. 보물 네 개를 모두 찾았어!",
                    "이제 바다 왕국의 문 앞까지만 가면 돼. 탐험 대장, 조금만 더 힘내자.",
                ),
                treasureLine = 1,
                effect = Effect.SEA,   // 파도와 갈매기 소리
            ),
        ),
        ending = "도착했어! {dest}에 온 걸 환영해. 보물 네 개가 빛나자 바다 왕국의 문이 스르르 열렸어. " +
            "바다 친구들이 모두 나와 탐험 대장에게 박수를 쳐 줬지. " +
            "오늘의 모험은 여기까지야. 끝까지 함께해 줘서 고마워.",
        placeScenes = mapOf(
            PlaceType.RIVER to "왼쪽을 봐! 지금 거대한 강을 건너고 있어! 반짝이는 물결을 찾아볼까? " +
                "저 물결은 바다 왕국으로 흘러가는 길이래.",
            PlaceType.TUNNEL to "어, 터널이다! 여기는 수정 동굴이야. 벽마다 수정이 숨어 있대. 무섭지 않아. " +
                "동굴 끝에는 언제나 빛이 있거든. 같이 셋까지 세어 볼까? 하나, 둘, 셋.",
            PlaceType.SEA to "와, 바다가 보여! 저기가 바로 바다 왕국이 있는 곳이야. " +
                "파도가 어서 오라고 손짓하고 있어.",
        ),
        restScene = "{name}에 도착했어. 탐험 대장도 잠깐 쉬어 가야지. 물도 마시고 기지개도 켜자. " +
            "쉬었다가 다시 출발하면 언덕 너머 바다를 찾아갈 거야!",
        restResume = "다시 출발! 탐험 대장, 바다 보물을 찾으러 가자.",
        beltPause = "어, 벨트가 풀렸네. 벨트를 매야 다시 출발할 수 있어!",
        beltResume = "좋아, 벨트를 맸구나. 다시 출발!",
        driverBeltAlert = "보호자님, 뒷좌석 안전벨트가 풀려 있어요. 확인해 주세요.",
        questDone = "찾았구나! 정말 잘했어, 탐험 대장.",
        bonusQuests = listOf(
            "다음 보물까지는 조금 더 가야 해. 그동안 보너스 퀘스트! 창밖에서 노란색 자동차를 찾아볼래? 찾으면, 찾았어! 하고 말해 줘.",
            "보너스 퀘스트! 창밖에서 가장 키가 큰 나무를 찾아봐. 찾으면, 찾았어! 하고 말해 줘.",
            "보너스 퀘스트! 하늘을 나는 새를 찾아볼까? 찾으면, 찾았어! 하고 말해 줘.",
            "보너스 퀘스트! 창밖에서 동그라미 모양을 찾아봐. 바퀴도 좋고 표지판도 좋아. 찾으면, 찾았어! 하고 말해 줘.",
            "보너스 퀘스트! 파란색 표지판을 찾아볼래? 찾으면, 찾았어! 하고 말해 줘.",
        ),
        remainingAnswer = "바다 보물 {count}만 더 찾으면 도착해! {time} 남았어.",
        remainingAnswerLast = "보물은 모두 찾았어. 이제 바다 왕국의 문까지만 가면 돼. {time} 남았어.",
    )
}
