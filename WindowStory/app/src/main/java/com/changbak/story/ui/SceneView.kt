package com.changbak.story.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/**
 * 동화책 느낌의 배경 장면. 하늘, 언덕, 호수, 마을, 목적지로 이어지는 길, 자동차, 이야기꾼 여우 루루를 그린다.
 *
 * 달리는 동안에는 길의 점선뿐 아니라 길가의 나무와 집, 땅의 줄무늬가 함께 다가와서
 * 자동차가 앞으로 나아가는 것처럼 보인다.
 * 길 위의 별은 앞으로 찾을 보물이고, 깃발은 목적지다.
 * 진행률이 오를수록 별과 깃발이 자동차 쪽으로 다가온다.
 */
class SceneView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    /** 0(출발)~1(도착). */
    var progress = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }
    var chapterCount = 4

    /** true면 길과 주변 풍경이 다가와 자동차가 달리는 것처럼 보인다. */
    var moving = false
        set(value) {
            if (field == value) return
            field = value
            lastFrame = SystemClock.uptimeMillis()
            invalidate()
        }

    /** 여우가 말하는 중이면 살짝 움직인다. */
    var talking = false
        set(value) {
            field = value
            invalidate()
        }

    /**
     * 길가의 나무나 집 하나. 화면 좌표가 아니라 "길을 따라 얼마나 멀리 있는지"(dist)로 위치를 기억한다.
     * 차가 달리면 dist가 줄어들어 점점 가까워지고 커지며, 화면 아래로 지나가면 다시 지평선에서 나타난다.
     */
    private class Scenery(
        val dist: Float,      // 0(바로 앞)~1(지평선). 정지 상태에서의 거리
        val side: Float,      // -1 길 왼쪽, +1 길 오른쪽
        val lateral: Float,   // 길에서 옆으로 떨어진 정도(길 반폭의 배수)
        val isHouse: Boolean,
        val size: Float,      // 나무 크기 또는 집 반폭
        val tall: Float,      // 집 높이
        val color: Int,
        val roof: Int,
    )

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val path = Path()
    private val rect = RectF()
    private var skyShader: Shader? = null
    private var groundShader: Shader? = null
    private var sunShader: Shader? = null

    /** dist 오름차순으로 정렬해 둔다. */
    private var scenery = emptyList<Scenery>()

    /** 지금까지 달린 거리(0~1을 되풀이). 길의 점선, 땅의 줄무늬, 나무와 집이 모두 이 값으로 함께 움직인다. */
    private var travel = 0f
    private var cloudShift = 0f
    private var lastFrame = 0L

    // ----- 길의 모양: u는 0(화면 아래)~1(멀리 지평선) -----
    private fun roadX(u: Float) = width * (0.705f - 0.03f * u + 0.075f * sin(u * PI.toFloat() * 2.1f) * (1f - 0.35f * u))
    private fun roadY(u: Float) = height * (0.31f + 0.72f * (1f - u).pow(1.6f))
    private fun roadHalf(u: Float) = 10f + width * 0.105f * (1f - u).pow(1.7f)

    /** 거리(0~1)를 화면의 길 위치 u로 바꾼다. 가까울수록 빨리 다가오는 것처럼 보이게 한다. */
    private fun distToU(dist: Float) = dist.pow(0.75f)

    /** 멀수록 작게 그리는 비율. 지평선에서는 0이라 새로 나타나는 것이 티 나지 않는다. */
    private fun depthScale(u: Float) = 1.3f * (1f - u).pow(1.5f)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val hf = h.toFloat()
        skyShader = LinearGradient(
            0f, 0f, 0f, hf * 0.5f,
            intArrayOf(0xFF8CC4F8.toInt(), 0xFFC9E3FB.toInt(), 0xFFFFE9D6.toInt()),
            floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP,
        )
        groundShader = LinearGradient(
            0f, hf * 0.36f, 0f, hf,
            intArrayOf(0xFFCDEBB4.toInt(), 0xFFA5D99A.toInt(), 0xFF86C98A.toInt()),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
        )
        sunShader = RadialGradient(
            w * 0.16f, hf * 0.6f, w * 0.42f,
            intArrayOf(0x99FFF3B8.toInt(), 0x00FFF3B8), null, Shader.TileMode.CLAMP,
        )
        if (scenery.isEmpty()) scenery = makeScenery()
    }

    /** 길 양옆에 나무와 집을 흩어 놓는다. 항상 같은 배치가 나온다. */
    private fun makeScenery(): List<Scenery> {
        var seed = 12345L
        fun rnd(): Float {
            seed = (seed * 1103515245L + 12345L) and 0x7fffffffL
            return seed / 2147483647f
        }
        val houseColors = intArrayOf(0xFFF7E3C8.toInt(), 0xFFE5D9F2.toInt(), 0xFFD6E6F7.toInt(), 0xFFFAD9D9.toInt())
        val roofColors = intArrayOf(0xFFE89A8B.toInt(), 0xFFA9A2D6.toInt(), 0xFF8DB7E2.toInt(), 0xFFE9A6B8.toInt())
        val list = mutableListOf<Scenery>()
        // 길이 화면 오른쪽에 있어서 왼쪽 들판이 넓다. 왼쪽에 더 많이, 더 멀리까지 놓는다.
        repeat(TREE_COUNT) { i ->
            val left = i % 10 < 7
            val lateral = if (left) 1.5f + 15f * rnd().pow(1.4f) else 1.5f + 5f * rnd()
            list.add(
                Scenery(
                    dist = rnd(), side = if (left) -1f else 1f, lateral = lateral, isHouse = false,
                    size = 46f + 30f * rnd(), tall = 0f,
                    color = TREE_TONES[(rnd() * TREE_TONES.size).toInt().coerceIn(0, TREE_TONES.size - 1)], roof = 0,
                ),
            )
        }
        repeat(HOUSE_COUNT) { i ->
            val left = i % 4 != 0
            val pick = (rnd() * 4).toInt().coerceIn(0, 3)
            list.add(
                Scenery(
                    dist = (i + rnd() * 0.8f) / HOUSE_COUNT, side = if (left) -1f else 1f,
                    lateral = if (left) 2.6f + 9f * rnd() else 2.6f + 3f * rnd(), isHouse = true,
                    size = 40f + 22f * rnd(), tall = 70f + 70f * rnd(),
                    color = houseColors[pick], roof = roofColors[pick],
                ),
            )
        }
        return list.sortedBy { it.dist }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val now = SystemClock.uptimeMillis()
        val dt = ((now - lastFrame).coerceIn(0L, 60L)) / 1000f
        lastFrame = now
        if (moving) {
            travel = (travel + dt * TRAVEL_PER_SEC) % 1f
            cloudShift = (cloudShift + dt * 3f) % (w * 1.4f)
        }

        drawSky(canvas, w, h)
        drawHills(canvas, w, h)
        drawGround(canvas, w, h)
        drawLake(canvas, w, h)
        drawGroundBands(canvas, w, h)
        drawRoad(canvas)
        drawScenery(canvas, nearerThanCar = false)
        drawMarkers(canvas)
        drawCar(canvas)
        drawScenery(canvas, nearerThanCar = true)
        drawFox(canvas, w * 0.125f, h * 0.715f, h / 1210f * 0.92f, now)

        if (moving || talking) postInvalidateOnAnimation()
    }

    private fun drawSky(canvas: Canvas, w: Float, h: Float) {
        fill.shader = skyShader
        canvas.drawRect(0f, 0f, w, h * 0.5f, fill)
        fill.shader = null
        drawCloud(canvas, (w * 0.50f + cloudShift) % (w * 1.4f) - w * 0.2f, h * 0.075f, 1.0f)
        drawCloud(canvas, (w * 0.95f + cloudShift * 0.6f) % (w * 1.4f) - w * 0.2f, h * 0.17f, 1.35f)
        drawCloud(canvas, (w * 0.20f + cloudShift * 0.4f) % (w * 1.4f) - w * 0.2f, h * 0.24f, 0.8f)
        drawStar(canvas, w * 0.40f, h * 0.065f, 16f, 0xFFFFE08A.toInt())
        drawStar(canvas, w * 0.63f, h * 0.20f, 10f, 0xFFFFE08A.toInt())
    }

    private fun drawCloud(canvas: Canvas, x: Float, y: Float, s: Float) {
        fill.color = 0xF2FFFFFF.toInt()
        canvas.drawCircle(x, y, 34f * s, fill)
        canvas.drawCircle(x + 40f * s, y - 16f * s, 44f * s, fill)
        canvas.drawCircle(x + 88f * s, y, 36f * s, fill)
        canvas.drawCircle(x + 44f * s, y + 14f * s, 40f * s, fill)
    }

    private fun drawHills(canvas: Canvas, w: Float, h: Float) {
        // 먼 산
        fill.color = 0xFFB6DDB9.toInt()
        path.reset()
        path.moveTo(0f, h * 0.40f)
        path.quadTo(w * 0.15f, h * 0.30f, w * 0.34f, h * 0.37f)
        path.quadTo(w * 0.52f, h * 0.22f, w * 0.74f, h * 0.34f)
        path.quadTo(w * 0.90f, h * 0.28f, w, h * 0.36f)
        path.lineTo(w, h * 0.5f)
        path.lineTo(0f, h * 0.5f)
        path.close()
        canvas.drawPath(path, fill)
        // 가까운 언덕
        fill.color = 0xFF9FD3A0.toInt()
        path.reset()
        path.moveTo(0f, h * 0.44f)
        path.quadTo(w * 0.25f, h * 0.35f, w * 0.48f, h * 0.41f)
        path.quadTo(w * 0.70f, h * 0.33f, w, h * 0.42f)
        path.lineTo(w, h * 0.52f)
        path.lineTo(0f, h * 0.52f)
        path.close()
        canvas.drawPath(path, fill)
    }

    private fun drawGround(canvas: Canvas, w: Float, h: Float) {
        fill.shader = groundShader
        canvas.drawRect(0f, h * 0.42f, w, h, fill)
        fill.shader = sunShader
        canvas.drawRect(0f, 0f, w, h, fill)
        fill.shader = null
    }

    /** 멀리 보이는 호수. 지평선 가까이에 있어서 달려도 거의 움직이지 않는다. */
    private fun drawLake(canvas: Canvas, w: Float, h: Float) {
        fill.color = 0xFF9DD2F7.toInt()
        path.reset()
        path.moveTo(0f, h * 0.405f)
        path.quadTo(w * 0.12f, h * 0.40f, w * 0.21f, h * 0.425f)
        path.quadTo(w * 0.15f, h * 0.465f, 0f, h * 0.47f)
        path.close()
        canvas.drawPath(path, fill)
        stroke.color = 0x99FFFFFF.toInt()
        stroke.strokeWidth = 3f
        for (i in 0 until 3) {
            val y = h * (0.42f + 0.014f * i)
            canvas.drawLine(w * (0.02f + 0.03f * i), y, w * (0.07f + 0.03f * i), y, stroke)
        }
    }

    /** 땅의 옅은 줄무늬. 길의 점선과 같은 속도로 다가와서 땅 전체가 뒤로 흐르는 것처럼 보인다. */
    private fun drawGroundBands(canvas: Canvas, w: Float, h: Float) {
        fill.color = 0x16FFFFFF
        val top = h * 0.445f
        for (i in 0 until BAND_COUNT) {
            val near = ((i.toFloat() / BAND_COUNT - travel) % 1f + 1f) % 1f
            val far = (near + 0.5f / BAND_COUNT).coerceAtMost(1f)
            val yNear = roadY(distToU(near))
            val yFar = roadY(distToU(far)).coerceAtLeast(top)
            if (yNear > yFar) canvas.drawRect(0f, yFar, w, yNear, fill)
        }
    }

    /**
     * 나무와 집을 먼 것부터 그린다. 자동차보다 앞쪽(가까운 쪽)에 있는 것은 자동차를 그린 뒤에 그려서
     * 앞뒤 순서가 맞게 한다.
     */
    private fun drawScenery(canvas: Canvas, nearerThanCar: Boolean) {
        val n = scenery.size
        if (n == 0) return
        // 지금 거리 = (처음 거리 - 달린 거리)를 0~1로 되돌린 값. 처음 거리가 travel 바로 아래인 것이 가장 멀다.
        var split = scenery.indexOfFirst { it.dist >= travel }
        if (split < 0) split = n
        for (k in 0 until n) {
            val index = ((split - 1 - k) % n + n) % n
            val item = scenery[index]
            val u = distToU(((item.dist - travel) % 1f + 1f) % 1f)
            if ((u < CAR_U) != nearerThanCar) continue
            val scale = depthScale(u)
            if (scale < 0.03f) continue
            val x = roadX(u) + item.side * roadHalf(u) * item.lateral
            val y = roadY(u)
            val reach = item.size * scale * 1.5f
            if (x < -reach || x > width + reach) continue
            if (item.isHouse) drawHouse(canvas, x, y, item.size * scale, item.tall * scale, item.color, item.roof)
            else drawTree(canvas, x, y, item.size * scale, item.color)
        }
    }

    private fun drawHouse(canvas: Canvas, x: Float, y: Float, hw: Float, hh: Float, color: Int, roof: Int) {
        fill.color = 0x1F000000
        rect.set(x - hw * 1.2f, y - hw * 0.14f, x + hw * 1.2f, y + hw * 0.18f)
        canvas.drawOval(rect, fill)
        fill.color = color
        rect.set(x - hw, y - hh, x + hw, y)
        canvas.drawRoundRect(rect, hw * 0.12f, hw * 0.12f, fill)
        // 세모 지붕
        fill.color = roof
        path.reset()
        path.moveTo(x - hw * 1.15f, y - hh + 2f)
        path.lineTo(x, y - hh - hw * 0.75f)
        path.lineTo(x + hw * 1.15f, y - hh + 2f)
        path.close()
        canvas.drawPath(path, fill)
        // 창문
        fill.color = 0xD9FFFFFF.toInt()
        val rows = if (hh > hw * 1.6f) 2 else 1
        for (r in 0 until rows) for (c in 0 until 2) {
            val cx = x - hw * 0.45f + hw * 0.9f * c
            val top = y - hh * (0.82f - 0.42f * r)
            rect.set(cx - hw * 0.2f, top, cx + hw * 0.2f, top + hh * 0.22f)
            canvas.drawRoundRect(rect, hw * 0.08f, hw * 0.08f, fill)
        }
    }

    private fun drawTree(canvas: Canvas, x: Float, y: Float, s: Float, color: Int) {
        fill.color = 0x22000000
        rect.set(x - s * 0.7f, y - s * 0.12f, x + s * 0.7f, y + s * 0.16f)
        canvas.drawOval(rect, fill)
        fill.color = 0xFFA9805E.toInt()
        rect.set(x - s * 0.1f, y - s * 0.7f, x + s * 0.1f, y)
        canvas.drawRoundRect(rect, s * 0.08f, s * 0.08f, fill)
        fill.color = color
        canvas.drawCircle(x, y - s * 1.05f, s * 0.62f, fill)
        canvas.drawCircle(x - s * 0.42f, y - s * 0.78f, s * 0.48f, fill)
        canvas.drawCircle(x + s * 0.42f, y - s * 0.78f, s * 0.48f, fill)
        fill.color = 0x33FFFFFF
        canvas.drawCircle(x - s * 0.18f, y - s * 1.2f, s * 0.26f, fill)
    }

    private fun drawRoad(canvas: Canvas) {
        // 길 바닥과 그 위의 파란 안내선
        fillRoad(canvas, 1.0f, 0xFFD3C9E0.toInt())
        fillRoad(canvas, 0.74f, 0xFF4F9DFF.toInt())
        fillRoad(canvas, 0.60f, 0xFF6DB0FF.toInt())
        // 가운데 점선: 달리는 중이면 아래로 흘러내린다
        fill.color = 0xE6FFFFFF.toInt()
        val count = DASH_COUNT
        for (i in 0 until count) {
            val z = ((i.toFloat() / count - travel) % 1f + 1f) % 1f
            val u0 = distToU(z)
            val u1 = (u0 + 0.012f + 0.03f * (1f - u0)).coerceAtMost(1f)
            path.reset()
            path.moveTo(roadX(u0) - roadHalf(u0) * 0.05f, roadY(u0))
            path.lineTo(roadX(u0) + roadHalf(u0) * 0.05f, roadY(u0))
            path.lineTo(roadX(u1) + roadHalf(u1) * 0.05f, roadY(u1))
            path.lineTo(roadX(u1) - roadHalf(u1) * 0.05f, roadY(u1))
            path.close()
            canvas.drawPath(path, fill)
        }
    }

    private fun fillRoad(canvas: Canvas, widthRatio: Float, color: Int) {
        fill.color = color
        path.reset()
        val steps = 48
        for (i in 0..steps) {
            val u = i / steps.toFloat()
            val x = roadX(u) - roadHalf(u) * widthRatio
            if (i == 0) path.moveTo(x, roadY(u)) else path.lineTo(x, roadY(u))
        }
        for (i in steps downTo 0) {
            val u = i / steps.toFloat()
            path.lineTo(roadX(u) + roadHalf(u) * widthRatio, roadY(u))
        }
        path.close()
        canvas.drawPath(path, fill)
    }

    /** 앞으로 찾을 보물(별)과 목적지 깃발. 앞으로 남은 구간(VIEW_AHEAD)만 길 위에 보인다. */
    private fun drawMarkers(canvas: Canvas) {
        for (i in 0 until chapterCount) {
            drawMarker(canvas, (i + StoryLineView.TREASURE_AT) / chapterCount, isGoal = false)
        }
        drawMarker(canvas, 1f, isGoal = true)
    }

    private fun drawMarker(canvas: Canvas, at: Float, isGoal: Boolean) {
        val ahead = at - progress
        if (ahead < 0f || ahead > VIEW_AHEAD) return
        val u = CAR_U + (1f - CAR_U) * (ahead / VIEW_AHEAD)
        val x = roadX(u)
        val y = roadY(u)
        val scale = 0.35f + 1.3f * (1f - u).pow(1.3f)
        if (isGoal) drawFlag(canvas, x, y, scale) else drawStar(canvas, x, y - 26f * scale, 26f * scale, 0xFFFFD45E.toInt())
    }

    private fun drawFlag(canvas: Canvas, x: Float, y: Float, s: Float) {
        fill.color = 0xFF5A5F7A.toInt()
        canvas.drawRect(x - 3f * s, y - 96f * s, x + 3f * s, y, fill)
        val cell = 14f * s
        for (r in 0 until 3) for (c in 0 until 4) {
            fill.color = if ((r + c) % 2 == 0) 0xFF3B4160.toInt() else Color.WHITE
            canvas.drawRect(x + 3f * s + c * cell, y - 96f * s + r * cell, x + 3f * s + (c + 1) * cell, y - 96f * s + (r + 1) * cell, fill)
        }
    }

    private fun drawStar(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        fill.color = color
        path.reset()
        for (i in 0 until 10) {
            val radius = if (i % 2 == 0) r else r * 0.48f
            val a = (-PI / 2 + i * PI / 5).toFloat()
            val x = cx + radius * kotlin.math.cos(a)
            val y = cy + radius * sin(a)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        canvas.drawPath(path, fill)
    }

    private fun drawCar(canvas: Canvas) {
        val x = roadX(CAR_U)
        val y = roadY(CAR_U)
        val s = width / 1682f
        // 위치를 알리는 파란 고리
        stroke.color = 0xAA5CB6FF.toInt()
        stroke.strokeWidth = 8f * s
        rect.set(x - 112f * s, y - 26f * s, x + 112f * s, y + 44f * s)
        canvas.drawOval(rect, stroke)
        fill.color = 0x33000000
        rect.set(x - 84f * s, y - 6f * s, x + 84f * s, y + 30f * s)
        canvas.drawOval(rect, fill)
        // 바퀴
        fill.color = 0xFF4A4F66.toInt()
        rect.set(x - 78f * s, y - 30f * s, x - 46f * s, y + 18f * s)
        canvas.drawRoundRect(rect, 12f * s, 12f * s, fill)
        rect.set(x + 46f * s, y - 30f * s, x + 78f * s, y + 18f * s)
        canvas.drawRoundRect(rect, 12f * s, 12f * s, fill)
        // 차체
        fill.color = 0xFFFDFDFF.toInt()
        rect.set(x - 82f * s, y - 96f * s, x + 82f * s, y + 2f * s)
        canvas.drawRoundRect(rect, 34f * s, 34f * s, fill)
        rect.set(x - 64f * s, y - 150f * s, x + 64f * s, y - 60f * s)
        canvas.drawRoundRect(rect, 36f * s, 36f * s, fill)
        // 뒷유리
        fill.color = 0xFF55607F.toInt()
        rect.set(x - 50f * s, y - 136f * s, x + 50f * s, y - 86f * s)
        canvas.drawRoundRect(rect, 22f * s, 22f * s, fill)
        // 후미등과 번호판
        fill.color = 0xFFFF8A8A.toInt()
        canvas.drawCircle(x - 58f * s, y - 46f * s, 13f * s, fill)
        canvas.drawCircle(x + 58f * s, y - 46f * s, 13f * s, fill)
        fill.color = 0xFFE3E6F2.toInt()
        rect.set(x - 26f * s, y - 40f * s, x + 26f * s, y - 20f * s)
        canvas.drawRoundRect(rect, 6f * s, 6f * s, fill)
    }

    /** 이야기꾼 여우 루루. 구름 위에 앉아 책을 읽어 준다. */
    private fun drawFox(canvas: Canvas, cx: Float, baseY: Float, s: Float, now: Long) {
        val bob = if (talking) sin(now / 260f) * 5f * s else 0f
        val cy = baseY + bob
        val orange = 0xFFF7A45E.toInt()
        val deep = 0xFFE98A45.toInt()
        val cream = 0xFFFFF4E4.toInt()
        val ink = 0xFF5B3A2E.toInt()

        // 앉아 있는 구름
        fill.color = 0xF7FFFFFF.toInt()
        canvas.drawCircle(cx - 170f * s, baseY + 250f * s, 110f * s, fill)
        canvas.drawCircle(cx - 30f * s, baseY + 270f * s, 140f * s, fill)
        canvas.drawCircle(cx + 150f * s, baseY + 262f * s, 120f * s, fill)
        canvas.drawCircle(cx + 290f * s, baseY + 300f * s, 90f * s, fill)
        fill.color = 0x55FFD9E6
        canvas.drawCircle(cx + 60f * s, baseY + 300f * s, 120f * s, fill)

        // 꼬리
        canvas.save()
        canvas.rotate(-28f, cx + 120f * s, cy + 150f * s)
        fill.color = deep
        rect.set(cx + 70f * s, cy + 60f * s, cx + 170f * s, cy + 210f * s)
        canvas.drawOval(rect, fill)
        fill.color = cream
        rect.set(cx + 88f * s, cy + 50f * s, cx + 152f * s, cy + 112f * s)
        canvas.drawOval(rect, fill)
        canvas.restore()

        // 몸
        fill.color = orange
        rect.set(cx - 92f * s, cy + 40f * s, cx + 92f * s, cy + 230f * s)
        canvas.drawOval(rect, fill)
        fill.color = cream
        rect.set(cx - 52f * s, cy + 70f * s, cx + 52f * s, cy + 220f * s)
        canvas.drawOval(rect, fill)

        // 귀
        for (side in intArrayOf(-1, 1)) {
            fill.color = orange
            path.reset()
            path.moveTo(cx + side * 98f * s, cy - 40f * s)
            path.lineTo(cx + side * 96f * s, cy - 178f * s)
            path.lineTo(cx + side * 22f * s, cy - 100f * s)
            path.close()
            canvas.drawPath(path, fill)
            fill.color = 0xFFFFD9C2.toInt()
            path.reset()
            path.moveTo(cx + side * 84f * s, cy - 62f * s)
            path.lineTo(cx + side * 86f * s, cy - 140f * s)
            path.lineTo(cx + side * 44f * s, cy - 96f * s)
            path.close()
            canvas.drawPath(path, fill)
        }

        // 얼굴
        fill.color = orange
        canvas.drawCircle(cx, cy - 20f * s, 104f * s, fill)
        fill.color = cream
        canvas.drawCircle(cx - 44f * s, cy + 16f * s, 52f * s, fill)
        canvas.drawCircle(cx + 44f * s, cy + 16f * s, 52f * s, fill)
        canvas.drawCircle(cx, cy + 34f * s, 46f * s, fill)
        fill.color = 0x66FF9FB5
        canvas.drawCircle(cx - 70f * s, cy + 8f * s, 20f * s, fill)
        canvas.drawCircle(cx + 70f * s, cy + 8f * s, 20f * s, fill)

        // 웃는 눈, 코, 입
        stroke.color = ink
        stroke.strokeWidth = 7f * s
        rect.set(cx - 62f * s, cy - 44f * s, cx - 26f * s, cy - 14f * s)
        canvas.drawArc(rect, 200f, 140f, false, stroke)
        rect.set(cx + 26f * s, cy - 44f * s, cx + 62f * s, cy - 14f * s)
        canvas.drawArc(rect, 200f, 140f, false, stroke)
        fill.color = ink
        rect.set(cx - 13f * s, cy + 2f * s, cx + 13f * s, cy + 20f * s)
        canvas.drawOval(rect, fill)
        stroke.strokeWidth = 5f * s
        val open = if (talking) 8f * s * (0.5f + 0.5f * sin(now / 120f)) else 0f
        rect.set(cx - 22f * s, cy + 14f * s, cx, cy + 36f * s + open)
        canvas.drawArc(rect, 20f, 140f, false, stroke)
        rect.set(cx, cy + 14f * s, cx + 22f * s, cy + 36f * s + open)
        canvas.drawArc(rect, 20f, 140f, false, stroke)

        // 책
        val by = cy + 150f * s
        fill.color = 0xFFF27C9B.toInt()
        path.reset()
        path.moveTo(cx - 150f * s, by - 44f * s)
        path.lineTo(cx, by - 20f * s)
        path.lineTo(cx + 150f * s, by - 44f * s)
        path.lineTo(cx + 150f * s, by + 70f * s)
        path.lineTo(cx, by + 94f * s)
        path.lineTo(cx - 150f * s, by + 70f * s)
        path.close()
        canvas.drawPath(path, fill)
        fill.color = 0xFFFFF7EC.toInt()
        path.reset()
        path.moveTo(cx - 136f * s, by - 56f * s)
        path.lineTo(cx, by - 32f * s)
        path.lineTo(cx + 136f * s, by - 56f * s)
        path.lineTo(cx + 136f * s, by - 30f * s)
        path.lineTo(cx, by - 6f * s)
        path.lineTo(cx - 136f * s, by - 30f * s)
        path.close()
        canvas.drawPath(path, fill)
        drawStar(canvas, cx + 76f * s, by + 30f * s, 22f * s, 0xFFFFE08A.toInt())
        // 책을 잡은 앞발
        fill.color = orange
        canvas.drawCircle(cx - 150f * s, by + 14f * s, 24f * s, fill)
        canvas.drawCircle(cx + 150f * s, by + 14f * s, 24f * s, fill)

        // 반짝이는 별
        drawStar(canvas, cx - 122f * s, cy - 130f * s, 24f * s, 0xFFFFD45E.toInt())
        drawStar(canvas, cx + 128f * s, cy - 196f * s, 16f * s, 0xFFFFE08A.toInt())
    }

    private companion object {
        /** 자동차가 길 위에 놓이는 위치. */
        const val CAR_U = 0.2f

        /** 길 위에 보이는 앞쪽 구간의 길이(전체 경로 대비). */
        const val VIEW_AHEAD = 0.3f

        /** 1초에 달리는 거리. 지평선의 나무가 화면 아래로 지나가기까지 약 30초 걸린다. */
        const val TRAVEL_PER_SEC = 0.033f
        const val DASH_COUNT = 16
        const val BAND_COUNT = 9
        const val TREE_COUNT = 64
        const val HOUSE_COUNT = 11
        val TREE_TONES = intArrayOf(0xFF74C277.toInt(), 0xFF8AD08A.toInt(), 0xFF63B36E.toInt())
    }
}
