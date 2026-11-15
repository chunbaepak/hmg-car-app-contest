package com.changbak.story.story

/** 이야기 한 장. lines는 한 번에 낭독하는 단위다. */
data class Chapter(
    val title: String,
    val lines: List<String>,
    /** 창밖을 보게 하는 과제. 없으면 null. */
    val quest: String? = null,
)

/** 이야기에 장면으로 끼어드는 장소 종류. */
enum class PlaceType { RIVER, TUNNEL, SEA }

/**
 * 이야기 한 편.
 * {dest}는 목적지 이름, {name}은 경유지 이름, {n}은 장 수로 바뀐다.
 */
data class Story(
    val title: String,
    val greeting: String,
    val tripIntro: String,
    val chapters: List<Chapter>,
    val ending: String,
    val placeScenes: Map<PlaceType, String>,
    val waypointScene: String,
    val beltPause: String,
    val beltResume: String,
    val questDone: String,
)

object StoryRepository {

    /** 내장 이야기 1편(4장). 네트워크 없이도 진행된다. */
    val luluAndTheStar = Story(
        title = "루루와 잃어버린 별",
        greeting = "안녕! 나는 이야기 친구야. 벨트를 잘 맸구나. " +
            "차가 출발하면 꼬마 여우 루루의 이야기를 들려줄게.",
        tripIntro = "자, 출발! 오늘 우리는 {dest}까지 가. " +
            "꼬마 여우 루루도 우리랑 같이 떠나기로 했어. " +
            "이야기는 모두 {n}개야. 마지막 이야기가 끝나면 우리도 도착해.",
        chapters = listOf(
            Chapter(
                title = "사라진 별",
                lines = listOf(
                    "숲속 마을에 꼬마 여우 루루가 살았어. 루루에게는 밤마다 머리맡을 밝혀 주는 작은 별, 반짝이가 있었지.",
                    "그런데 어느 날 아침, 반짝이가 보이지 않는 거야. 창가에는 편지 한 장만 놓여 있었어.",
                    "편지에는 이렇게 적혀 있었어. 나는 길 끝에서 기다릴게. 루루는 가방을 메고 길을 나섰어.",
                ),
                quest = "루루가 길을 잘 찾으려면 도움이 필요해. 창밖에서 빨간 자동차를 찾아볼래? " +
                    "찾으면, 찾았어! 하고 말해 줘.",
            ),
            Chapter(
                title = "느림보 거북이",
                lines = listOf(
                    "길을 걷던 루루는 느림보 거북이 할아버지를 만났어. 할아버지는 등에 커다란 지도를 지고 있었지.",
                    "할아버지가 말했어. 서두르지 않아도 된단다. 길은 한 걸음씩 가면 끝이 나오거든.",
                    "루루는 할아버지와 나란히 걸으며 구름 모양 맞히기 놀이를 했어.",
                ),
                quest = "우리도 해 볼까? 창밖 하늘에서 재미있는 모양의 구름을 찾아봐. " +
                    "찾으면, 찾았어! 하고 말해 줘.",
            ),
            Chapter(
                title = "바람 부는 언덕",
                lines = listOf(
                    "언덕에 오르자 바람이 세게 불었어. 루루의 지도가 바람에 날아가 버렸지 뭐야.",
                    "루루는 잠깐 겁이 났지만, 거북이 할아버지의 말을 떠올렸어. 한 걸음씩 가면 끝이 나온다.",
                    "루루가 고개를 들자, 멀리서 작은 빛이 깜빡였어. 반짝이가 보내는 신호였어.",
                ),
            ),
            Chapter(
                title = "길 끝의 빛",
                lines = listOf(
                    "빛은 점점 가까워졌어. 루루의 발걸음도 점점 빨라졌지.",
                    "길가의 꽃들이 속삭였어. 거의 다 왔어, 조금만 더.",
                    "이제 정말 조금만 가면 돼. 루루는 두근두근하는 마음으로 마지막 길을 걸었어.",
                ),
            ),
        ),
        ending = "도착했어! {dest}에 온 걸 환영해. 길 끝에는 반짝이가 기다리고 있었어. " +
            "반짝이가 말했지. 네가 혼자서도 먼 길을 올 수 있다는 걸 보여 주고 싶었어. " +
            "루루와 반짝이는 꼭 껴안았어. 오늘 이야기는 여기까지야. 끝까지 함께해 줘서 고마워.",
        placeScenes = mapOf(
            PlaceType.RIVER to "창밖을 봐! 강이 보이지? 루루도 지금 반짝이는 강을 건너고 있어. " +
                "물고기들이 루루에게 힘내라고 꼬리를 흔들어 줬대.",
            PlaceType.TUNNEL to "어, 터널이다! 루루도 깜깜한 동굴에 들어갔어. 무섭지 않아. " +
                "동굴 끝에는 언제나 빛이 있거든. 우리 같이 셋까지 세어 볼까? 하나, 둘, 셋.",
            PlaceType.SEA to "와, 바다가 보여! 루루는 태어나서 처음 바다를 봤어. " +
                "파도가 루루의 발을 간질였대.",
        ),
        waypointScene = "{name}에 도착했어. 루루도 나무 그늘에서 쉬어 가기로 했어. " +
            "물도 마시고 기지개도 켜자. 다시 출발하면 다음 이야기를 들려줄게.",
        beltPause = "어, 벨트가 풀렸네. 벨트를 매야 루루가 다시 출발할 수 있어.",
        beltResume = "좋아, 벨트를 맸구나. 루루도 다시 출발!",
        questDone = "찾았구나! 정말 잘했어. 덕분에 루루가 길을 찾았어.",
    )
}
