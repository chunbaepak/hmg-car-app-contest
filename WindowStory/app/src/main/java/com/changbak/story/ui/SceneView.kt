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
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin

/**
 * 동화책 느낌의 배경 장면. 하늘, 언덕, 강, 마을, 목적지로 이어지는 길, 자동차, 이야기꾼 여우 루루를 그린다.
 *
 * 길 위의 별은 다음 장이 시작되는 지점이고, 깃발은 목적지다.
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

    /** true면 길의 점선이 흘러 자동차가 달리는 것처럼 보인다. */
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

    private class Tree(val x: Float, val y: Float, val size: Float, val tone: Int)
    private class House(val x: Float, val y: Float, val w: Float, val h: Float, val color: Int, val roof: Int)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val path = Path()
    private val rect = RectF()
    private var skyShader: Shader? = null
    private var groundShader: Shader? = null
    private var sunShader: Shader? = null
    private val trees = mutableListOf<Tree>()
    private val houses = mutableListOf<House>()

    private var roadPhase = 0f
    private var cloudShift = 0f
    private var lastFrame = 0L

    // ----- 길의 모양: u는 0(화면 아래)~1(멀리 지평선) -----
    private fun roadX(u: Float) = width * (0.705f - 0.03f * u + 0.075f * sin(u * PI.toFloat() * 2.1f) * (1f - 0.35f * u))
    private fun roadY(u: Float) = height * (0.31f + 0.72f * (1f - u).pow(1.6f))
    private fun roadHalf(u: Float) = 10f + width * 0.105f * (1f - u).pow(1.7f)

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
        placeScenery(w.toFloat(), hf)
    }

    /** 나무와 집을 길과 강을 피해 흩어 놓는다. 위치는 항상 같다. */
    private fun placeScenery(w: Float, h: Float) {
        trees.clear()
        houses.clear()
        var seed = 12345L
        fun rnd(): Float {
            seed = (seed * 1103515245L + 12345L) and 0x7fffffffL
            return seed / 2147483647f
        }
        fun freeOfRoad(x: Float, y: Float, margin: Float): Boolean {
            val u = (1f - ((y / h - 0.31f) / 0.72f).coerceIn(0f, 1f).pow(1f / 1.6f))
            return abs(x - roadX(u)) > roadHalf(u) + margin
        }
        val houseColors = intArrayOf(0xFFF7E3C8.toInt(), 0xFFE5D9F2.toInt(), 0xFFD6E6F7.toInt(), 0xFFFAD9D9.toInt())
        val roofColors = intArrayOf(0xFFE89A8B.toInt(), 0xFFA9A2D6.toInt(), 0xFF8DB7E2.toInt(), 0xFFE9A6B8.toInt())
        repeat(60) {
            val y = h * (0.40f + 0.24f * rnd())
            val x = w * (0.30f + 0.70f * rnd())
            val scale = ((y / h - 0.34f) / 0.3f).coerceIn(0.25f, 1.2f)
            val hw = 44f * scale * (0.8f + 0.5f * rnd())
            val hh = 78f * scale * (0.7f + 0.9f * rnd())
            val pick = (rnd() * 4).toInt().coerceIn(0, 3)
            val tooClose = houses.any { abs(it.x - x) < (it.w + hw) * 1.25f && abs(it.y - y) < 60f }
            if (houses.size < 12 && !tooClose && freeOfRoad(x, y, hw + 24f)) {
                houses.add(House(x, y, hw, hh, houseColors[pick], roofColors[pick]))
            }
        }
        repeat(220) {
            val y = h * (0.39f + 0.60f * rnd())
            val x = w * rnd()
            val depth = ((y / h - 0.36f) / 0.64f).coerceIn(0f, 1f)
            val size = 16f + 70f * depth.pow(1.4f) * (0.7f + 0.6f * rnd())
            val inRiver = x < w * 0.17f && y < h * 0.62f
            val inFoxCorner = x < w * 0.36f && y > h * 0.60f
            val underBar = y > h * 0.86f && x > w * 0.25f
            if (trees.size < 70 && !inRiver && !inFoxCorner && !underBar && freeOfRoad(x, y, size + 26f)) {
                trees.add(Tree(x, y, size, (rnd() * 3).toInt()))
            }
        }
        trees.sortBy { it.y }
        houses.sortBy { it.y }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val now = SystemClock.uptimeMillis()
        val dt = ((now - lastFrame).coerceIn(0L, 60L)) / 1000f
        lastFrame = now
        if (moving) {
            roadPhase = (roadPhase + dt * 0.9f) % 1f
            cloudShift = (cloudShift + dt * 6f) % (w * 1.4f)
        }

        drawSky(canvas, w, h)
        drawHills(canvas, w, h)
        drawGround(canvas, w, h)
        drawRiver(canvas, w, h)
        houses.forEach { drawHouse(canvas, it) }
        drawRoad(canvas)
        trees.forEach { drawTree(canvas, it) }
        drawMarkers(canvas)
        drawCar(canvas)
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

    private fun drawRiver(canvas: Canvas, w: Float, h: Float) {
        fill.color = 0xFF8FCBF6.toInt()
        path.reset()
        path.moveTo(0f, h * 0.39f)
        path.quadTo(w * 0.10f, h * 0.40f, w * 0.16f, h * 0.47f)
        path.quadTo(w * 0.20f, h * 0.55f, w * 0.10f, h * 0.63f)
        path.lineTo(0f, h * 0.66f)
        path.close()
        canvas.drawPath(path, fill)
        stroke.color = 0x99FFFFFF.toInt()
        stroke.strokeWidth = 4f
        for (i in 0 until 5) {
            val y = h * (0.43f + 0.04f * i)
            canvas.drawLine(w * (0.01f + 0.015f * (i % 2)), y, w * (0.05f + 0.015f * (i % 2)), y, stroke)
        }
        // 다리
        fill.color = 0xFFF6EBDD.toInt()
        canvas.drawRect(0f, h * 0.405f, w * 0.14f, h * 0.425f, fill)
        fill.color = 0xFF8FCBF6.toInt()
        for (i in 0 until 3) {
            rect.set(w * (0.008f + 0.045f * i), h * 0.412f, w * (0.04f + 0.045f * i), h * 0.46f)
            canvas.drawArc(rect, 180f, 180f, true, fill)
        }
    }

    private fun drawHouse(canvas: Canvas, house: House) {
        fill.color = house.color
        rect.set(house.x - house.w, house.y - house.h, house.x + house.w, house.y)
        canvas.drawRoundRect(rect, 6f, 6f, fill)
        // 세모 지붕
        fill.color = house.roof
        path.reset()
        path.moveTo(house.x - house.w * 1.15f, house.y - house.h + 2f)
        path.lineTo(house.x, house.y - house.h - house.w * 0.75f)
        path.lineTo(house.x + house.w * 1.15f, house.y - house.h + 2f)
        path.close()
        canvas.drawPath(path, fill)
        // 창문
        fill.color = 0xD9FFFFFF.toInt()
        val rows = if (house.h > house.w * 1.6f) 2 else 1
        for (r in 0 until rows) for (c in 0 until 2) {
            val cx = house.x - house.w * 0.45f + house.w * 0.9f * c
            val top = house.y - house.h * (0.82f - 0.42f * r)
            rect.set(cx - house.w * 0.2f, top, cx + house.w * 0.2f, top + house.h * 0.22f)
            canvas.drawRoundRect(rect, 4f, 4f, fill)
        }
    }

    private fun drawTree(canvas: Canvas, tree: Tree) {
        val s = tree.size
        fill.color = 0x22000000
        rect.set(tree.x - s * 0.7f, tree.y - s * 0.12f, tree.x + s * 0.7f, tree.y + s * 0.16f)
        canvas.drawOval(rect, fill)
        fill.color = 0xFFA9805E.toInt()
        rect.set(tree.x - s * 0.1f, tree.y - s * 0.7f, tree.x + s * 0.1f, tree.y)
        canvas.drawRoundRect(rect, s * 0.08f, s * 0.08f, fill)
        fill.color = TREE_TONES[tree.tone % TREE_TONES.size]
        canvas.drawCircle(tree.x, tree.y - s * 1.05f, s * 0.62f, fill)
        canvas.drawCircle(tree.x - s * 0.42f, tree.y - s * 0.78f, s * 0.48f, fill)
        canvas.drawCircle(tree.x + s * 0.42f, tree.y - s * 0.78f, s * 0.48f, fill)
        fill.color = 0x33FFFFFF
        canvas.drawCircle(tree.x - s * 0.18f, tree.y - s * 1.2f, s * 0.26f, fill)
    }

    private fun drawRoad(canvas: Canvas) {
        // 길 바닥과 그 위의 파란 안내선
        fillRoad(canvas, 1.0f, 0xFFD3C9E0.toInt())
        fillRoad(canvas, 0.74f, 0xFF4F9DFF.toInt())
        fillRoad(canvas, 0.60f, 0xFF6DB0FF.toInt())
        // 가운데 점선: 달리는 중이면 아래로 흘러내린다
        fill.color = 0xE6FFFFFF.toInt()
        val count = 16
        for (i in 0 until count) {
            val z = ((i - roadPhase) / count)
            if (z < 0f) continue
            val u0 = z.pow(0.75f)
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

    /** 다음 장의 별과 목적지 깃발. 앞으로 남은 구간(VIEW_AHEAD)만 길 위에 보인다. */
    private fun drawMarkers(canvas: Canvas) {
        for (i in 1..chapterCount) {
            val ahead = i / chapterCount.toFloat() - progress
            if (ahead < 0f || ahead > VIEW_AHEAD) continue
            val u = CAR_U + (1f - CAR_U) * (ahead / VIEW_AHEAD)
            val x = roadX(u)
            val y = roadY(u)
            val scale = 0.35f + 1.3f * (1f - u).pow(1.3f)
            if (i == chapterCount) drawFlag(canvas, x, y, scale) else drawStar(canvas, x, y - 26f * scale, 26f * scale, 0xFFFFD45E.toInt())
        }
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
        val TREE_TONES = intArrayOf(0xFF74C277.toInt(), 0xFF8AD08A.toInt(), 0xFF63B36E.toInt())
    }
}
