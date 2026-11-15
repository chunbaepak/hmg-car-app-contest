package com.changbak.story.story

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

enum class Effect { CHIME, WATER, TUNNEL, SEA, FANFARE }
enum class MusicState { PLAYING, PAUSED, STOPPED }
enum class Phase { WAITING, GREETED, PLAYING, PAUSED_BELT, ENDED }

/** 엔진이 바깥(낭독, 소리, 화면)에 시키는 일. */
interface StoryOutput {
    /** text를 낭독하고, 끝나면 onDone을 한 번 부른다. */
    fun speak(text: String, onDone: () -> Unit)
    fun stopSpeaking()
    fun playEffect(effect: Effect)
    fun setMusic(state: MusicState)
    fun onChapterChanged(index: Int, title: String)
    fun onLineChanged(text: String)
    fun onPhaseChanged(phase: Phase)
    fun onQuestChanged(active: Boolean)
}

/**
 * 이동 정보(진행률, 남은 시간, 장소, 도착)와 차량 신호(벨트)를 받아 이야기를 진행한다.
 * 실제 내비와 시연용 경로가 같은 입력을 주므로, 엔진은 둘을 구분하지 않는다.
 * 모든 함수는 메인 스레드에서 부른다.
 */
class StoryEngine(
    private val story: Story,
    private val out: StoryOutput,
    private val postDelayed: (delayMs: Long, block: () -> Unit) -> Unit,
) {
    private class Item(
        val text: String,
        val effect: Effect? = null,
        val chapter: Int? = null,
        val quest: Boolean = false,
        val onSpoken: (() -> Unit)? = null,
    )

    private val queue = ArrayDeque<Item>()
    private var current: Item? = null

    /** 낭독을 끊을 때마다 올린다. 예전 낭독의 완료 콜백을 무시하는 데 쓴다. */
    private var generation = 0

    var phase = Phase.WAITING
        private set
    var chapterIndex = -1
        private set
    val chapterCount get() = story.chapters.size
    val title get() = story.title

    private var queuedChapter = -1
    private var destName = DEFAULT_DEST
    private var remainingSec = 0
    private var beltOk = true
    private var greeted = false
    private var tripActive = false
    private var questActive = false
    private val seenPlaces = mutableSetOf<PlaceType>()

    /** 뒷좌석에 아이가 타고 벨트를 맸을 때. 출발 전 한 번만 인사한다. */
    fun onChildReady() {
        if (greeted || tripActive) return
        greeted = true
        setPhase(Phase.GREETED)
        queue.addLast(Item(story.greeting))
        pump()
    }

    fun onTripStarted(dest: String, totalSec: Int) {
        interrupt(requeueCurrent = false)
        queue.clear()
        tripActive = true
        destName = dest.ifBlank { DEFAULT_DEST }
        remainingSec = totalSec
        chapterIndex = -1
        queuedChapter = -1
        seenPlaces.clear()
        setQuest(false)
        out.onChapterChanged(-1, "")
        queue.addLast(
            Item(
                story.tripIntro.replace("{dest}", destName).replace("{n}", chapterCount.toString()),
                effect = Effect.CHIME,
            ),
        )
        enqueueChapter(0)
        if (beltOk) {
            out.setMusic(MusicState.PLAYING)
            setPhase(Phase.PLAYING)
            pump()
        } else {
            enterBeltPause()
        }
    }

    /** fraction은 0(출발)~1(도착). 진행률에 맞춰 다음 장을 대기열에 넣는다. */
    fun onProgress(fraction: Float, remainingSec: Int) {
        if (!tripActive) return
        this.remainingSec = remainingSec
        val target = min(chapterCount - 1, max(0, (fraction * chapterCount).toInt()))
        while (queuedChapter < target) enqueueChapter(queuedChapter + 1)
    }

    /** 강, 터널, 바다 같은 장소에 들어섰을 때. 장소마다 한 번만 장면을 넣는다. */
    fun onPlace(type: PlaceType) {
        if (!tripActive || !seenPlaces.add(type)) return
        val scene = story.placeScenes[type] ?: return
        val effect = when (type) {
            PlaceType.RIVER -> Effect.WATER
            PlaceType.TUNNEL -> Effect.TUNNEL
            PlaceType.SEA -> Effect.SEA
        }
        queue.addFirst(Item(scene, effect = effect))
        pump()
    }

    fun onWaypointArrived(name: String) {
        if (!tripActive) return
        queue.addFirst(Item(story.waypointScene.replace("{name}", name.ifBlank { "쉬어 가는 곳" })))
        pump()
    }

    fun onArrived() {
        if (!tripActive) return
        tripActive = false
        interrupt(requeueCurrent = false)
        queue.clear()
        setQuest(false)
        remainingSec = 0
        out.setMusic(MusicState.PLAYING)
        if (phase != Phase.PLAYING) setPhase(Phase.PLAYING)
        queue.addLast(
            Item(
                story.ending.replace("{dest}", destName),
                effect = Effect.FANFARE,
                onSpoken = {
                    setPhase(Phase.ENDED)
                    postDelayed(ENDING_MUSIC_MS) { if (phase == Phase.ENDED) out.setMusic(MusicState.STOPPED) }
                },
            ),
        )
        pump()
    }

    fun onTripCancelled() {
        if (!tripActive) return
        tripActive = false
        interrupt(requeueCurrent = false)
        queue.clear()
        setQuest(false)
        out.setMusic(MusicState.STOPPED)
        out.onLineChanged("")
        setPhase(Phase.WAITING)
    }

    /** ok가 false면 주행 중에 벨트가 풀린 상태다. */
    fun onBeltChanged(ok: Boolean) {
        if (beltOk == ok) return
        beltOk = ok
        if (!tripActive) return
        if (!ok) {
            enterBeltPause()
        } else {
            interrupt(requeueCurrent = false)
            out.setMusic(MusicState.PLAYING)
            setPhase(Phase.PLAYING)
            queue.addFirst(Item(story.beltResume))
            pump()
        }
    }

    /** 아이가 "얼마나 남았어?"라고 물었을 때. */
    fun onAskRemaining() {
        val text = remainingAnswer()
        if (phase == Phase.PAUSED_BELT) {
            speakOnly("$text 그런데 먼저 벨트를 매 줘.")
            return
        }
        interrupt(requeueCurrent = true)
        queue.addFirst(Item(text))
        pump()
    }

    /** 아이가 과제를 해냈다고 말했을 때("찾았어!"). */
    fun onQuestFound() {
        if (!questActive || phase == Phase.PAUSED_BELT) return
        setQuest(false)
        interrupt(requeueCurrent = true)
        queue.addFirst(Item(story.questDone))
        pump()
    }

    /** 화면에 보여 줄 남은 분량. 예: "약 48분  |  이야기 2개" */
    fun remainingShort(): String {
        if (!tripActive) return if (phase == Phase.ENDED) "도착했어요" else "출발 전"
        val left = chaptersLeft()
        val stories = if (left <= 0) "마지막 이야기" else "이야기 ${left}개"
        return "${formatTime(remainingSec)}  |  $stories"
    }

    /** 지금 듣는 장의 제목. 아직 시작 전이면 null. */
    fun currentChapterTitle(): String? = story.chapters.getOrNull(chapterIndex)?.title

    private fun remainingAnswer(): String {
        if (!tripActive) {
            return if (phase == Phase.ENDED) "벌써 도착했어!" else "아직 출발하지 않았어. 출발하면 알려 줄게."
        }
        val left = chaptersLeft()
        val time = formatTime(remainingSec)
        return if (left <= 0) {
            "지금 듣는 이야기가 마지막이야. 조금만 더 가면 도착해. $time 남았어."
        } else {
            "이야기 ${counter(left)}만 더 들으면 도착이야. $time 남았어."
        }
    }

    /** 지금 듣는 장 뒤에 남은 장 수. */
    private fun chaptersLeft() = chapterCount - 1 - max(chapterIndex, 0)

    private fun enqueueChapter(index: Int) {
        queuedChapter = index
        val chapter = story.chapters[index]
        queue.addLast(Item("${index + 1}장. ${chapter.title}.", effect = Effect.CHIME, chapter = index))
        chapter.lines.forEach { queue.addLast(Item(it)) }
        chapter.quest?.let { queue.addLast(Item(it, quest = true)) }
        pump()
    }

    private fun enterBeltPause() {
        // 읽던 문장은 벨트를 다시 맨 뒤 처음부터 읽는다.
        interrupt(requeueCurrent = true)
        out.setMusic(MusicState.PAUSED)
        setPhase(Phase.PAUSED_BELT)
        speakOnly(story.beltPause)
    }

    /** 대기열과 상관없이 한 문장만 말한다. 끝나도 다음으로 넘어가지 않는다. */
    private fun speakOnly(text: String) {
        interrupt(requeueCurrent = true)
        out.onLineChanged(text)
        out.speak(text) {}
    }

    private fun interrupt(requeueCurrent: Boolean) {
        val item = current
        current = null
        generation++
        out.stopSpeaking()
        if (requeueCurrent && item != null) queue.addFirst(item)
    }

    private fun pump() {
        if (current != null || phase == Phase.PAUSED_BELT) return
        val item = queue.removeFirstOrNull() ?: return
        current = item
        val gen = generation
        item.chapter?.let {
            chapterIndex = it
            setQuest(false)
            out.onChapterChanged(it, story.chapters[it].title)
        }
        item.effect?.let(out::playEffect)
        out.onLineChanged(item.text)
        out.speak(item.text) {
            if (gen != generation) return@speak
            current = null
            if (item.quest) setQuest(true)
            item.onSpoken?.invoke()
            postDelayed(GAP_MS) { if (gen == generation) pump() }
        }
    }

    private fun setQuest(active: Boolean) {
        if (questActive == active) return
        questActive = active
        out.onQuestChanged(active)
    }

    private fun setPhase(next: Phase) {
        if (phase == next) return
        phase = next
        out.onPhaseChanged(next)
    }

    companion object {
        private const val DEFAULT_DEST = "목적지"
        private const val GAP_MS = 900L
        private const val ENDING_MUSIC_MS = 8_000L

        fun formatTime(sec: Int): String {
            val minutes = ceil(sec / 60.0).toInt()
            return when {
                minutes <= 1 -> "약 1분"
                minutes < 60 -> "약 ${minutes}분"
                minutes % 60 == 0 -> "약 ${minutes / 60}시간"
                else -> "약 ${minutes / 60}시간 ${minutes % 60}분"
            }
        }

        private fun counter(n: Int) = when (n) {
            1 -> "한 개"
            2 -> "두 개"
            3 -> "세 개"
            4 -> "네 개"
            else -> "${n}개"
        }
    }
}
