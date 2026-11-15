package com.changbak.story.story

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

enum class Effect { CHIME, WATER, TUNNEL, SEA, FANFARE }
enum class MusicState { PLAYING, PAUSED, STOPPED }
enum class Phase { WAITING, GREETED, PLAYING, PAUSED_BELT, RESTING, ENDED }

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
    fun onTreasuresChanged(found: Int)
}

/**
 * 이동 정보(진행률, 남은 시간, 장소, 경유지, 도착)와 차량 신호(벨트)를 받아 이야기를 진행한다.
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
        /** 이 문장이 속한 장. 장과 상관없는 문장은 -1. */
        val ofChapter: Int = -1,
        /** 장의 첫 문장(제목)인가. */
        val startsChapter: Boolean = false,
        /** 창밖 과제인가. 읽고 나서 잠시 대답을 기다린다. */
        val quest: Boolean = false,
        /** 이야기가 밀리면 건너뛰어도 되는 문장인가(과제, 보너스 과제). */
        val optional: Boolean = false,
        /** 이 문장을 읽고 나면 보물을 하나 찾은 것으로 센다. */
        val treasure: Boolean = false,
        /** 쉬는 중이거나 대답을 기다리는 중에도 바로 말해야 하는 문장인가. */
        val urgent: Boolean = false,
        val rest: Boolean = false,
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

    /** 목적지에 도착했는가. 엔딩을 읽는 동안에도 true다. */
    var arrived = false
        private set

    /** 경유지(휴게소)에서 쉬는 중인가. 다시 출발하면 onRestEnded로 알려 줘야 한다. */
    var resting = false
        private set
    var restName = ""
        private set
    var treasuresFound = 0
        private set

    private var queuedChapter = -1
    private var destName = DEFAULT_DEST
    private var remainingSec = 0
    private var lastFraction = 0f
    private var beltOk = true
    private var greeted = false
    private var tripActive = false
    private var questActive = false
    private val seenPlaces = mutableSetOf<PlaceType>()

    /** 휴게소 장면을 다 읽고, 다시 출발하기를 기다리는 중. */
    private var restHold = false

    /** 과제를 내고 아이의 대답을 기다리는 중. */
    private var questHold = false
    private var questToken = 0
    private var beltToken = 0
    private var idleToken = 0
    private var bonusIndex = 0
    private var bonusInChapter = 0
    private var bonusIntervalMs = MIN_BONUS_INTERVAL_MS

    /** 뒷좌석에 아이가 타고 벨트를 맸을 때. 출발 전 한 번만 인사한다. */
    fun onChildReady() {
        if (greeted || tripActive) return
        greeted = true
        setPhase(Phase.GREETED)
        queue.addLast(Item(story.greeting))
        pump()
    }

    /**
     * @param totalSec 내비가 알려 준 예상 소요 시간
     * @param realTotalSec 실제로 흐를 시간. 시연용 경로는 빨리 감기라서 totalSec보다 짧다.
     */
    fun onTripStarted(dest: String, totalSec: Int, realTotalSec: Int) {
        interrupt(requeueCurrent = false)
        queue.clear()
        tripActive = true
        arrived = false
        resting = false
        restHold = false
        questHold = false
        questToken++
        beltToken++
        idleToken++
        destName = dest.ifBlank { DEFAULT_DEST }
        remainingSec = totalSec
        lastFraction = 0f
        chapterIndex = -1
        queuedChapter = -1
        bonusInChapter = 0
        // 한 장에 보너스 과제가 두 번쯤 들어갈 간격. 이동이 길수록 간격도 길다.
        bonusIntervalMs = (realTotalSec * 1000L / chapterCount / 3)
            .coerceIn(MIN_BONUS_INTERVAL_MS, MAX_BONUS_INTERVAL_MS)
        seenPlaces.clear()
        setQuest(false)
        setTreasures(0)
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
        lastFraction = fraction
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

    /** 경유지(휴게소)에 도착했을 때. 쉬어 가는 장면을 읽고, 다시 출발할 때까지 이야기를 멈춘다. */
    fun onWaypointArrived(name: String) {
        if (!tripActive) return
        resting = true
        restHold = false
        restName = name.ifBlank { "쉬어 가는 곳" }
        // 쉬는 동안에는 하던 과제를 접는다.
        setQuest(false)
        if (questHold) {
            questHold = false
            questToken++
        }
        queue.addFirst(
            Item(
                story.restScene.replace("{name}", restName),
                rest = true,
                onSpoken = {
                    if (resting) {
                        restHold = true
                        if (phase == Phase.PLAYING) setPhase(Phase.RESTING)
                    }
                },
            ),
        )
        pump()
    }

    /** 쉬었다가 다시 출발했을 때. */
    fun onRestEnded() {
        if (!resting) return
        resting = false
        val scenePending = queue.any { it.rest }
        restHold = false
        if (phase == Phase.RESTING) setPhase(Phase.PLAYING)
        if (scenePending) {
            // 쉬어 가는 장면을 읽기도 전에 다시 출발했다면 그 장면은 건너뛴다.
            queue.removeAll { it.rest }
        } else {
            queue.addFirst(Item(story.restResume, effect = Effect.CHIME))
        }
        pump()
    }

    fun onArrived() {
        if (!tripActive) return
        tripActive = false
        arrived = true
        resting = false
        restHold = false
        questHold = false
        questToken++
        beltToken++
        idleToken++
        interrupt(requeueCurrent = false)
        queue.clear()
        setQuest(false)
        setTreasures(chapterCount)
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
        resting = false
        restHold = false
        questHold = false
        questToken++
        beltToken++
        idleToken++
        interrupt(requeueCurrent = false)
        queue.clear()
        setQuest(false)
        setTreasures(0)
        out.setMusic(MusicState.STOPPED)
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
            beltToken++
            interrupt(requeueCurrent = false)
            out.setMusic(MusicState.PLAYING)
            setPhase(if (restHold) Phase.RESTING else Phase.PLAYING)
            queue.addFirst(Item(story.beltResume, urgent = true))
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
        queue.addFirst(Item(text, urgent = true))
        pump()
    }

    /** 아이가 과제를 해냈다고 말했을 때("찾았어!"). */
    fun onQuestFound() {
        if (!questActive || phase == Phase.PAUSED_BELT) return
        setQuest(false)
        questHold = false
        questToken++
        interrupt(requeueCurrent = true)
        queue.addFirst(Item(story.questDone, urgent = true))
        pump()
    }

    /** 화면에 보여 줄 남은 분량. 예: "약 48분  |  보물 2개 남음" */
    fun remainingShort(): String {
        if (!tripActive) return if (arrived) "도착했어요" else "출발 전"
        val left = chapterCount - treasuresFound
        val treasures = if (left <= 0) "보물을 다 찾았어요" else "보물 ${left}개 남음"
        return "${formatTime(remainingSec)}  |  $treasures"
    }

    /** 지금 듣는 장의 제목. 아직 시작 전이면 null. */
    fun currentChapterTitle(): String? = story.chapters.getOrNull(chapterIndex)?.title

    private fun remainingAnswer(): String {
        if (!tripActive) {
            return if (arrived) "벌써 도착했어!" else "아직 출발하지 않았어. 출발하면 알려 줄게."
        }
        val left = chapterCount - treasuresFound
        val template = if (left <= 0) story.remainingAnswerLast else story.remainingAnswer
        return template.replace("{count}", counter(left)).replace("{time}", formatTime(remainingSec))
    }

    private fun enqueueChapter(index: Int) {
        queuedChapter = index
        // 이야기가 밀려 있으면 줄인다: 아직 못 읽은 과제는 건너뛰고, 두 장 넘게 밀린 장은 제목만 남긴다.
        queue.removeAll { it.optional || (it.ofChapter in 0..index - 2 && !it.startsChapter) }
        if (questHold) {
            questHold = false
            questToken++
        }
        val chapter = story.chapters[index]
        queue.addLast(
            Item("${index + 1}장. ${chapter.title}.", effect = chapter.effect, ofChapter = index, startsChapter = true),
        )
        chapter.lines.forEachIndexed { i, line ->
            queue.addLast(Item(line, ofChapter = index, treasure = i == chapter.treasureLine))
        }
        chapter.quest?.let { queue.addLast(Item(it, ofChapter = index, quest = true, optional = true)) }
        pump()
    }

    private fun enterBeltPause() {
        // 읽던 문장은 벨트를 다시 맨 뒤 처음부터 읽는다.
        interrupt(requeueCurrent = true)
        out.setMusic(MusicState.PAUSED)
        setPhase(Phase.PAUSED_BELT)
        speakOnly(story.beltPause)
        scheduleDriverAlert(BELT_ALERT_FIRST_MS)
    }

    /** 벨트를 계속 매지 않으면 운전자에게 알린다. */
    private fun scheduleDriverAlert(delayMs: Long) {
        val token = ++beltToken
        postDelayed(delayMs) {
            if (token != beltToken || phase != Phase.PAUSED_BELT) return@postDelayed
            out.playEffect(Effect.CHIME)
            speakOnly(story.driverBeltAlert)
            scheduleDriverAlert(BELT_ALERT_REPEAT_MS)
        }
    }

    /** 다음 장까지 시간이 남으면(길이 막힐 때 등) 보너스 과제를 하나 끼워 넣는다. */
    private fun scheduleBonus() {
        if (!tripActive || story.bonusQuests.isEmpty()) return
        if (bonusInChapter >= MAX_BONUS_PER_CHAPTER || lastFraction > NO_BONUS_AFTER) return
        val token = ++idleToken
        postDelayed(bonusIntervalMs) {
            val idle = token == idleToken && tripActive && current == null && queue.isEmpty() &&
                phase == Phase.PLAYING && !restHold && !questHold
            if (!idle) return@postDelayed
            bonusInChapter++
            val text = story.bonusQuests[bonusIndex++ % story.bonusQuests.size]
            queue.addLast(Item(text, ofChapter = chapterIndex, quest = true, optional = true))
            pump()
        }
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
        // 쉬는 중이거나 대답을 기다리는 중에는 급한 말(질문의 대답 등)만 한다.
        if ((restHold || questHold) && queue.firstOrNull()?.urgent != true) return
        val item = queue.removeFirstOrNull()
        if (item == null) {
            scheduleBonus()
            return
        }
        idleToken++
        current = item
        val gen = generation
        if (item.startsChapter) {
            chapterIndex = item.ofChapter
            bonusInChapter = 0
            setQuest(false)
            setTreasures(max(treasuresFound, item.ofChapter))   // 건너뛴 장의 보물도 찾은 것으로 센다
            out.onChapterChanged(item.ofChapter, story.chapters[item.ofChapter].title)
        }
        item.effect?.let(out::playEffect)
        out.onLineChanged(item.text)
        out.speak(item.text) {
            if (gen != generation) return@speak
            current = null
            if (item.treasure) setTreasures(max(treasuresFound, item.ofChapter + 1))
            if (item.quest) {
                setQuest(true)
                questHold = true
                val token = ++questToken
                postDelayed(QUEST_WAIT_MS) {
                    if (token == questToken && questHold) {
                        questHold = false
                        pump()
                    }
                }
            }
            item.onSpoken?.invoke()
            postDelayed(GAP_MS) { if (gen == generation) pump() }
        }
    }

    private fun setQuest(active: Boolean) {
        if (questActive == active) return
        questActive = active
        out.onQuestChanged(active)
    }

    private fun setTreasures(found: Int) {
        val value = found.coerceIn(0, chapterCount)
        if (treasuresFound == value) return
        treasuresFound = value
        out.onTreasuresChanged(value)
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

        /** 과제를 내고 대답을 기다리는 시간. */
        private const val QUEST_WAIT_MS = 7_000L

        /** 벨트가 풀린 채로 이만큼 지나면 운전자에게 알린다. */
        private const val BELT_ALERT_FIRST_MS = 15_000L
        private const val BELT_ALERT_REPEAT_MS = 40_000L
        private const val MIN_BONUS_INTERVAL_MS = 30_000L
        private const val MAX_BONUS_INTERVAL_MS = 240_000L
        private const val MAX_BONUS_PER_CHAPTER = 2

        /** 도착이 가까우면 보너스 과제를 내지 않는다. */
        private const val NO_BONUS_AFTER = 0.92f

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
