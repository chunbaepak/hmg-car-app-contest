package com.changbak.story

import ai.pleos.playground.vehicle.constant.control.VehicleGear
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.changbak.story.audio.MusicPlayer
import com.changbak.story.audio.MusicPrefs
import com.changbak.story.story.Effect
import com.changbak.story.story.MusicState
import com.changbak.story.story.Phase
import com.changbak.story.story.PlaceType
import com.changbak.story.story.StoryEngine
import com.changbak.story.story.StoryOutput
import com.changbak.story.story.StoryRepository
import com.changbak.story.trip.DemoTripSource
import com.changbak.story.trip.NaviTripSource
import com.changbak.story.trip.PlaceAlerts
import com.changbak.story.trip.TripListener
import com.changbak.story.ui.SceneView
import com.changbak.story.vehicle.VehicleSignals
import com.changbak.story.voice.Narrator

/**
 * 창밖 이야기: 경로와 남은 시간에 맞춰 동화를 들려주는 뒷좌석 아이용 앱.
 *
 * 이동 정보(TripListener)와 차량 신호(VehicleSignals)를 받아 StoryEngine에 넘기고,
 * 엔진이 시키는 낭독, 소리, 화면 갱신(StoryOutput)을 실행한다.
 */
class MainActivity : Activity(), StoryOutput, TripListener, VehicleSignals.Listener {

    private val handler = Handler(Looper.getMainLooper())

    private lateinit var prefs: MusicPrefs
    private lateinit var music: MusicPlayer
    private lateinit var narrator: Narrator
    private lateinit var engine: StoryEngine
    private lateinit var demo: DemoTripSource
    private lateinit var navi: NaviTripSource
    private lateinit var vehicle: VehicleSignals
    private var placeAlerts: PlaceAlerts? = null

    private var vehicleState = VehicleSignals.State(null, null, null)
    private var virtualUnbuckled = false
    private var tripRunning = false
    private var tripFromDemo = false
    private var locationAsked = false

    /**
     * 에뮬레이터는 뒷좌석 벨트 착용 신호를 바꿀 방법이 없어 항상 "풀림"으로 온다.
     * 그래서 에뮬레이터에서는 시연 도구의 가상 벨트로 대신하고, 실제 차량에서는 차량 신호를 쓴다.
     */
    private val useVirtualBelt =
        Build.MODEL.contains("emulator", ignoreCase = true) || Build.HARDWARE.contains("ranchu", ignoreCase = true)

    private lateinit var sceneView: SceneView
    private lateinit var chapterBig: TextView
    private lateinit var chapterLabel: TextView
    private lateinit var statusLine: TextView
    private lateinit var remainBig: TextView
    private lateinit var lineText: TextView
    private lateinit var storySub: TextView
    private lateinit var statusText: TextView
    private lateinit var questBanner: View
    private lateinit var demoPanel: View
    private lateinit var settingsOverlay: View
    private lateinit var promptText: TextView
    private lateinit var startButton: Button
    private lateinit var speedButton: Button
    private lateinit var beltButton: Button
    private lateinit var musicButton: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)
        bindViews()

        prefs = MusicPrefs(this)
        music = MusicPlayer(this, ::log).apply { setTrack(prefs.mood.trackRes) }
        narrator = Narrator(this, ::log).apply {
            onSpeakingChanged = { speaking ->
                music.setDucked(speaking)
                sceneView.talking = speaking
            }
        }
        engine = StoryEngine(StoryRepository.luluAndTheStar, this) { delayMs, block ->
            handler.postDelayed(block, delayMs)
        }
        demo = DemoTripSource(this)
        navi = NaviTripSource(this, this)
        vehicle = VehicleSignals(this, this)

        findViewById<TextView>(R.id.storyTitle).text = engine.title
        sceneView.chapterCount = engine.chapterCount
        lineText.text = "안녕! 뒷좌석에 앉아 벨트를 매면 내가 인사할게. 출발하면 이야기가 시작돼."
        setupButtons()
        setupSettings()
        showChapterPill(Phase.WAITING)
        refreshStatus()

        narrator.start()
        vehicle.start()
        navi.start()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        demo.release()
        navi.release()
        vehicle.release()
        placeAlerts?.release()
        narrator.release()
        music.release()
        super.onDestroy()
    }

    // ---------- 화면 ----------

    private fun bindViews() {
        sceneView = findViewById(R.id.sceneView)
        chapterBig = findViewById(R.id.chapterBig)
        chapterLabel = findViewById(R.id.chapterLabel)
        statusLine = findViewById(R.id.statusLine)
        remainBig = findViewById(R.id.remainBig)
        lineText = findViewById(R.id.lineText)
        storySub = findViewById(R.id.storySub)
        statusText = findViewById(R.id.statusText)
        questBanner = findViewById(R.id.questBanner)
        demoPanel = findViewById(R.id.demoPanel)
        settingsOverlay = findViewById(R.id.settingsOverlay)
        promptText = findViewById(R.id.promptText)
        startButton = findViewById(R.id.startButton)
        speedButton = findViewById(R.id.speedButton)
        beltButton = findViewById(R.id.beltButton)
        musicButton = findViewById(R.id.musicButton)
    }

    private fun setupButtons() {
        startButton.setOnClickListener {
            if (demo.running) demo.cancel() else demo.start()
        }
        findViewById<ImageButton>(R.id.askButton).setOnClickListener { engine.onAskRemaining() }
        findViewById<Button>(R.id.questButton).setOnClickListener { engine.onQuestFound() }
        musicButton.setOnClickListener {
            // 음악 취향은 정차 중에만 바꿀 수 있다.
            if (!isMoving()) {
                refreshSettings()
                demoPanel.visibility = View.GONE
                settingsOverlay.visibility = View.VISIBLE
            }
        }
        findViewById<ImageButton>(R.id.menuButton).setOnClickListener {
            demoPanel.visibility = if (demoPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        speedButton.setOnClickListener {
            val all = DemoTripSource.Speed.entries
            demo.speed = all[(demo.speed.ordinal + 1) % all.size]
            refreshStatus()
        }
        beltButton.setOnClickListener {
            virtualUnbuckled = !virtualUnbuckled
            refreshStatus()
            updateBelt()
        }
        findViewById<Button>(R.id.settingsClose).setOnClickListener { settingsOverlay.visibility = View.GONE }
    }

    private fun setupSettings() {
        fillChoices(findViewById(R.id.moodRow), MusicPrefs.Mood.entries.map { it.label }) {
            prefs.mood = MusicPrefs.Mood.entries[it]
            music.setTrack(prefs.mood.trackRes)
            refreshSettings()
            refreshStatus()
        }
        fillChoices(findViewById(R.id.tempoRow), MusicPrefs.Tempo.entries.map { it.label }) {
            prefs.tempo = MusicPrefs.Tempo.entries[it]
            refreshSettings()
        }
        fillChoices(findViewById(R.id.instrumentRow), MusicPrefs.Instrument.entries.map { it.label }) {
            prefs.instrument = MusicPrefs.Instrument.entries[it]
            refreshSettings()
        }
    }

    private fun fillChoices(row: LinearLayout, labels: List<String>, onPick: (Int) -> Unit) {
        labels.forEachIndexed { index, text ->
            val button = Button(this, null, 0, R.style.ChoiceButton).apply {
                this.text = text
                gravity = Gravity.CENTER
                setOnClickListener { onPick(index) }
            }
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            if (index > 0) params.marginStart = 12
            row.addView(button, params)
        }
    }

    private fun refreshSettings() {
        selectChild(findViewById(R.id.moodRow), prefs.mood.ordinal)
        selectChild(findViewById(R.id.tempoRow), prefs.tempo.ordinal)
        selectChild(findViewById(R.id.instrumentRow), prefs.instrument.ordinal)
        promptText.text = prefs.sunoPrompt()
    }

    private fun selectChild(row: LinearLayout, selected: Int) {
        for (i in 0 until row.childCount) row.getChildAt(i).isSelected = i == selected
    }

    /** 왼쪽 위 알약: 지금 듣는 장. 이야기 진행 단계에 따라 글이 바뀐다. */
    private fun showChapterPill(phase: Phase) {
        val title = engine.currentChapterTitle()
        val (big, label) = when {
            phase == Phase.PAUSED_BELT -> "잠깐 멈춤" to "벨트를 매 주세요"
            phase == Phase.ENDED -> "도착!" to "오늘의 이야기 끝"
            phase == Phase.WAITING -> "출발 전" to "출발하면 이야기가 시작돼요"
            phase == Phase.GREETED -> "안녕!" to "이야기 친구 루루예요"
            title != null -> "${engine.chapterIndex + 1}장" to title
            else -> "출발!" to "이야기를 시작해요"
        }
        chapterBig.text = big
        chapterLabel.text = label
    }

    private fun refreshStatus() {
        val seat = when (vehicleState.rearOccupied) {
            true -> "뒷좌석 탑승"
            false -> "뒷좌석 비어 있음"
            null -> "뒷좌석 확인 중"
        }
        val unbuckled = if (useVirtualBelt) virtualUnbuckled else vehicleState.rearBuckled == false
        val belt = when {
            unbuckled -> "벨트 풀림"
            useVirtualBelt -> "벨트 착용(가상)"
            vehicleState.rearBuckled == true -> "벨트 착용"
            else -> "벨트 확인 중"
        }
        val gear = "기어 " + when (vehicleState.gear) {
            VehicleGear.GEAR_PARK -> "P"
            VehicleGear.GEAR_REVERSE -> "R"
            VehicleGear.GEAR_NEUTRAL -> "N"
            null, VehicleGear.GEAR_UNKNOWN -> "확인 중"
            else -> "D"
        }
        val mode = when {
            tripRunning && tripFromDemo -> "시연 경로"
            tripRunning -> "내비 안내 중"
            else -> "대기 중"
        }
        statusLine.text = "$seat · $belt · $gear · $mode"
        statusLine.setTextColor(getColor(if (unbuckled) R.color.warn else R.color.ink))

        startButton.text = if (demo.running) "시연 멈춤" else "시연 출발"
        startButton.isEnabled = demo.running || !tripRunning
        speedButton.text = "속도: ${demo.speed.label}"
        speedButton.isEnabled = !demo.running
        beltButton.text = if (virtualUnbuckled) "가상 벨트 매기" else "가상 벨트 풀기"
        beltButton.visibility = if (useVirtualBelt) View.VISIBLE else View.GONE

        val moving = isMoving()
        musicButton.isEnabled = !moving
        musicButton.alpha = if (moving) 0.5f else 1f
        if (moving) settingsOverlay.visibility = View.GONE

        storySub.text = "창밖 이야기 · ${prefs.mood.label} 테마"
        remainBig.text = engine.remainingShort()
        sceneView.moving = tripRunning && engine.phase != Phase.PAUSED_BELT
    }

    private fun log(message: String) {
        Log.i(TAG, message)
        statusText.text = message
    }

    // ---------- 차량 신호 ----------

    /** 차가 움직이는 중인가. 시연 경로를 재생 중이거나, 기어가 P가 아니면 움직이는 것으로 본다. */
    private fun isMoving() =
        demo.running || (vehicleState.gear != null && vehicleState.gear != VehicleGear.GEAR_UNKNOWN && !vehicleState.parked)

    override fun onVehicleChanged(state: VehicleSignals.State) {
        vehicleState = state
        val buckled = if (useVirtualBelt) !virtualUnbuckled else state.rearBuckled == true
        if (state.rearOccupied == true && buckled) engine.onChildReady()
        if (state.parked) startPlaceAlertsIfAllowed()
        Log.i(TAG, "차량 상태: 뒷좌석=${state.rearOccupied}, 벨트=${state.rearBuckled}, 기어=${state.gear}")
        refreshStatus()
        updateBelt()
    }

    override fun onVehicleLog(message: String) = log(message)

    /** 움직이는 중에 벨트가 풀리면 이야기를 멈추고, 다시 매면 이어 간다. */
    private fun updateBelt() {
        val unbuckled = if (useVirtualBelt) virtualUnbuckled else vehicleState.unbuckledWhileSeated
        engine.onBeltChanged(ok = !(isMoving() && unbuckled))
    }

    // ---------- 이동 정보 ----------

    override fun onTripStarted(destName: String, totalSec: Int, fromDemo: Boolean) {
        if (!fromDemo && demo.running) demo.release()   // 실제 안내가 시작되면 시연을 끝낸다.
        tripRunning = true
        tripFromDemo = fromDemo
        sceneView.progress = 0f
        music.setTrack(prefs.mood.trackRes)
        engine.onTripStarted(destName, totalSec)
        refreshStatus()
        updateBelt()
    }

    override fun onProgress(fraction: Float, remainingSec: Int, remainingMeters: Int) {
        if (!tripRunning) return
        sceneView.progress = fraction
        engine.onProgress(fraction, remainingSec)
        remainBig.text = engine.remainingShort()
    }

    override fun onPlace(type: PlaceType, name: String) {
        if (!tripRunning) return
        log("장소: $name")
        engine.onPlace(type)
    }

    override fun onWaypointArrived(name: String) {
        if (!tripRunning) return
        log("경유지 도착: $name")
        engine.onWaypointArrived(name)
    }

    override fun onArrived(name: String) {
        if (!tripRunning) return
        tripRunning = false
        sceneView.progress = 1f
        log("목적지 도착: $name")
        engine.onArrived()
        refreshStatus()
    }

    override fun onCancelled() {
        tripRunning = false
        engine.onTripCancelled()
        sceneView.progress = 0f
        lineText.text = "시연을 멈췄어. 다시 출발하면 이야기를 처음부터 들려줄게."
        log("이동이 취소됨")
        refreshStatus()
    }

    override fun onTripLog(message: String) = log(message)

    /**
     * 위치 권한이 있으면 장소 근접 알림을 켠다. 권한 요청 창은 주행 중에 띄울 수 없으므로,
     * 정차(P) 중일 때 한 번만 요청한다.
     */
    private fun startPlaceAlertsIfAllowed() {
        if (placeAlerts != null) return
        if (checkSelfPermission(LOCATION_PERMISSION) == PackageManager.PERMISSION_GRANTED) {
            placeAlerts = PlaceAlerts(this, { type, name -> if (tripRunning && !tripFromDemo) onPlace(type, name) }, ::log)
                .also { it.start() }
        } else if (!locationAsked) {
            locationAsked = true
            requestPermissions(arrayOf(LOCATION_PERMISSION), REQUEST_LOCATION)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_LOCATION) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startPlaceAlertsIfAllowed()
        } else {
            log("위치 권한이 없어 실제 주행의 장소 장면은 나오지 않아요")
        }
    }

    // ---------- 이야기 엔진이 시키는 일 ----------

    override fun speak(text: String, onDone: () -> Unit) = narrator.speak(text, onDone)

    override fun stopSpeaking() = narrator.stop()

    override fun playEffect(effect: Effect) = music.playEffect(effect)

    override fun setMusic(state: MusicState) = music.setState(state)

    override fun onChapterChanged(index: Int, title: String) {
        showChapterPill(engine.phase)
        remainBig.text = engine.remainingShort()
    }

    override fun onLineChanged(text: String) {
        if (text.isEmpty()) return
        lineText.text = text
        Log.i(TAG, "낭독: $text")
    }

    override fun onPhaseChanged(phase: Phase) {
        Log.i(TAG, "단계: $phase")
        showChapterPill(phase)
        refreshStatus()
    }

    override fun onQuestChanged(active: Boolean) {
        questBanner.visibility = if (active) View.VISIBLE else View.GONE
    }

    private companion object {
        const val TAG = "WindowStory"
        const val LOCATION_PERMISSION = "pleos.car.permission.FUSED_LOCATION"
        const val REQUEST_LOCATION = 7
    }
}
