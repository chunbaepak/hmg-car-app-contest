package com.changbak.story.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 이야기 진행선. 왼쪽(출발)에서 오른쪽(도착)으로 이어지는 선 위에
 * 찾아야 할 보물(별)과 이야기 속 이벤트(강, 휴게소, 터널, 바다)를 그림으로 놓는다.
 * 여우 얼굴이 지금 위치를 따라 움직이고 찾은 보물은 노랗게 켜져서,
 * 아이가 이야기가 얼마나 진행됐고 보물이 몇 개 남았는지 눈으로 볼 수 있다.
 */
class StoryLineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    enum class Kind { RIVER, TUNNEL, REST, SEA }

    private class Stop(val at: Float, val kind: Kind)

    /** 0(출발)~1(도착). */
    var progress = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }
    var chapterCount = 4
        set(value) {
            field = value
            invalidate()
        }

    /** 지금까지 찾은 보물 수. 장마다 하나씩 찾는다. */
    var treasuresFound = 0
        set(value) {
            field = value
            invalidate()
        }

    private val stops = mutableListOf<Stop>()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val path = Path()
    private val rect = RectF()

    /** 경로에서 미리 알고 있는 이벤트를 한꺼번에 놓는다. 비우려면 빈 목록을 준다. */
    fun setStops(list: List<Pair<Float, Kind>>) {
        stops.clear()
        list.forEach { stops.add(Stop(it.first.coerceIn(0f, 1f), it.second)) }
        invalidate()
    }

    /** 달리는 중에 만난 이벤트를 그 자리에 추가한다(실제 주행에서는 미리 알 수 없는 장소가 있다). */
    fun addStop(at: Float, kind: Kind) {
        stops.add(Stop(at.coerceIn(0f, 1f), kind))
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val left = h * 0.62f
        val right = w - h * 0.62f
        val lineY = h * 0.68f
        val iconY = h * 0.30f
        val iconR = h * 0.22f
        fun xAt(f: Float) = left + (right - left) * f

        // 선: 전체 길과 지나온 길
        stroke.strokeWidth = h * 0.06f
        stroke.color = TRACK
        canvas.drawLine(left, lineY, right, lineY, stroke)
        stroke.color = DONE
        canvas.drawLine(left, lineY, xAt(progress), lineY, stroke)

        // 이벤트 그림: 선 위쪽에 놓고 가는 줄로 선과 잇는다
        stroke.strokeWidth = 3f
        for (stop in stops) {
            val x = xAt(stop.at)
            val passed = stop.at <= progress + 0.001f
            stroke.color = if (passed) DONE else TRACK
            canvas.drawLine(x, iconY + iconR, x, lineY, stroke)
            drawIcon(canvas, x, iconY, iconR, stop.kind, passed)
        }
        // 출발(집)과 도착(깃발)
        drawHome(canvas, xAt(0f) - iconR * 1.5f, iconY + iconR * 0.25f, iconR * 0.8f)
        stroke.color = if (progress >= 1f) DONE else TRACK
        stroke.strokeWidth = 3f
        canvas.drawLine(xAt(1f), iconY + iconR, xAt(1f), lineY, stroke)
        drawGoal(canvas, xAt(1f), iconY, iconR, progress >= 1f)

        // 보물: 장마다 하나씩, 그 장의 뒤쪽에 놓인다. 찾으면 노랗게 켜진다.
        for (i in 0 until chapterCount) {
            val x = xAt((i + TREASURE_AT) / chapterCount)
            val found = i < treasuresFound
            fill.color = WHITE
            canvas.drawCircle(x, lineY, h * 0.16f, fill)
            drawStar(canvas, x, lineY, h * (if (found) 0.15f else 0.12f), if (found) STAR else STAR_OFF)
        }

        drawFoxMarker(canvas, xAt(progress), lineY, h * 0.15f)
    }

    /** 이벤트 그림 하나. 아직 지나지 않은 것은 흐리게 그린다. */
    private fun drawIcon(canvas: Canvas, cx: Float, cy: Float, r: Float, kind: Kind, passed: Boolean) {
        val alpha = if (passed) 230 else 95
        rect.set(cx - r - 2f, cy - r - 2f, cx + r + 2f, cy + r + 2f)
        val layer = canvas.saveLayerAlpha(rect, alpha)
        when (kind) {
            Kind.RIVER -> {
                fill.color = 0xFF7DC4F5.toInt()
                canvas.drawCircle(cx, cy, r, fill)
                drawWave(canvas, cx, cy - r * 0.22f, r * 0.62f, WHITE, r * 0.16f)
                drawWave(canvas, cx, cy + r * 0.28f, r * 0.62f, WHITE, r * 0.16f)
            }
            Kind.TUNNEL -> {
                fill.color = 0xFFC9BFD6.toInt()
                canvas.drawCircle(cx, cy, r, fill)
                fill.color = 0xFF55607F.toInt()
                rect.set(cx - r * 0.55f, cy - r * 0.5f, cx + r * 0.55f, cy + r * 1.1f)
                canvas.save()
                canvas.clipRect(cx - r, cy - r, cx + r, cy + r * 0.52f)
                canvas.drawOval(rect, fill)
                canvas.restore()
                // 터널 끝의 빛
                fill.color = 0xFFFFE08A.toInt()
                canvas.drawCircle(cx, cy + r * 0.2f, r * 0.16f, fill)
            }
            Kind.REST -> {
                fill.color = 0xFFFFF1D6.toInt()
                canvas.drawCircle(cx, cy, r, fill)
                fill.color = 0xFFA9805E.toInt()
                rect.set(cx - r * 0.09f, cy, cx + r * 0.09f, cy + r * 0.62f)
                canvas.drawRoundRect(rect, r * 0.05f, r * 0.05f, fill)
                fill.color = 0xFF74C277.toInt()
                canvas.drawCircle(cx, cy - r * 0.22f, r * 0.42f, fill)
                canvas.drawCircle(cx - r * 0.3f, cy + r * 0.02f, r * 0.3f, fill)
                canvas.drawCircle(cx + r * 0.3f, cy + r * 0.02f, r * 0.3f, fill)
            }
            Kind.SEA -> {
                fill.color = 0xFFBFE6FB.toInt()
                canvas.drawCircle(cx, cy, r, fill)
                fill.color = 0xFFFFD45E.toInt()
                canvas.drawCircle(cx + r * 0.28f, cy - r * 0.32f, r * 0.26f, fill)
                drawWave(canvas, cx, cy + r * 0.2f, r * 0.66f, 0xFF4F9DFF.toInt(), r * 0.17f)
                drawWave(canvas, cx, cy + r * 0.55f, r * 0.5f, 0xFF4F9DFF.toInt(), r * 0.17f)
            }
        }
        canvas.restoreToCount(layer)
    }

    private fun drawWave(canvas: Canvas, cx: Float, y: Float, half: Float, color: Int, thick: Float) {
        stroke.color = color
        stroke.strokeWidth = thick
        path.reset()
        path.moveTo(cx - half, y)
        path.quadTo(cx - half * 0.5f, y - half * 0.45f, cx, y)
        path.quadTo(cx + half * 0.5f, y + half * 0.45f, cx + half, y)
        canvas.drawPath(path, stroke)
    }

    private fun drawHome(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        fill.color = 0xFFF7E3C8.toInt()
        rect.set(cx - r * 0.62f, cy - r * 0.1f, cx + r * 0.62f, cy + r * 0.8f)
        canvas.drawRoundRect(rect, r * 0.1f, r * 0.1f, fill)
        fill.color = 0xFFE89A8B.toInt()
        path.reset()
        path.moveTo(cx - r * 0.85f, cy - r * 0.05f)
        path.lineTo(cx, cy - r * 0.85f)
        path.lineTo(cx + r * 0.85f, cy - r * 0.05f)
        path.close()
        canvas.drawPath(path, fill)
        fill.color = 0xFFA9805E.toInt()
        rect.set(cx - r * 0.16f, cy + r * 0.3f, cx + r * 0.16f, cy + r * 0.8f)
        canvas.drawRect(rect, fill)
    }

    private fun drawGoal(canvas: Canvas, cx: Float, cy: Float, r: Float, reached: Boolean) {
        fill.color = if (reached) 0xFFFFE08A.toInt() else 0xFFFFF7EC.toInt()
        canvas.drawCircle(cx, cy, r, fill)
        fill.color = INK
        canvas.drawRect(cx - r * 0.42f, cy - r * 0.6f, cx - r * 0.3f, cy + r * 0.62f, fill)
        fill.color = PINK
        path.reset()
        path.moveTo(cx - r * 0.3f, cy - r * 0.6f)
        path.lineTo(cx + r * 0.58f, cy - r * 0.28f)
        path.lineTo(cx - r * 0.3f, cy + r * 0.06f)
        path.close()
        canvas.drawPath(path, fill)
    }

    private fun drawStar(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        fill.color = color
        path.reset()
        for (i in 0 until 10) {
            val radius = if (i % 2 == 0) r else r * 0.48f
            val a = (-PI / 2 + i * PI / 5).toFloat()
            val x = cx + radius * cos(a)
            val y = cy + radius * sin(a)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        canvas.drawPath(path, fill)
    }

    /** 지금 위치를 알려 주는 여우 얼굴. */
    private fun drawFoxMarker(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        fill.color = 0x1F000000
        canvas.drawCircle(cx, cy + r * 0.1f, r * 1.2f, fill)
        fill.color = WHITE
        canvas.drawCircle(cx, cy, r * 1.2f, fill)
        val orange = 0xFFF7A45E.toInt()
        fill.color = orange
        for (side in intArrayOf(-1, 1)) {
            path.reset()
            path.moveTo(cx + side * r * 0.95f, cy - r * 0.2f)
            path.lineTo(cx + side * r * 0.9f, cy - r * 1.25f)
            path.lineTo(cx + side * r * 0.15f, cy - r * 0.7f)
            path.close()
            canvas.drawPath(path, fill)
        }
        canvas.drawCircle(cx, cy, r * 0.92f, fill)
        fill.color = 0xFFFFF4E4.toInt()
        canvas.drawCircle(cx - r * 0.36f, cy + r * 0.34f, r * 0.42f, fill)
        canvas.drawCircle(cx + r * 0.36f, cy + r * 0.34f, r * 0.42f, fill)
        fill.color = 0xFF5B3A2E.toInt()
        canvas.drawCircle(cx - r * 0.36f, cy - r * 0.12f, r * 0.1f, fill)
        canvas.drawCircle(cx + r * 0.36f, cy - r * 0.12f, r * 0.1f, fill)
        rect.set(cx - r * 0.14f, cy + r * 0.12f, cx + r * 0.14f, cy + r * 0.32f)
        canvas.drawOval(rect, fill)
    }

    companion object {
        /** 보물이 놓이는 자리: 각 장의 4분의 3 지점. 화면의 길 위 별과 같은 값을 쓴다. */
        const val TREASURE_AT = 0.75f
        private const val TRACK = 0xFFE3E6F1.toInt()
        private const val DONE = 0xFF8DBDF7.toInt()
        private const val WHITE = 0xFFFFFFFF.toInt()
        private const val STAR = 0xFFFFD45E.toInt()
        private const val STAR_OFF = 0xFFD3D7E6.toInt()
        private const val PINK = 0xFFF27C9B.toInt()
        private const val INK = 0xFF3B4160.toInt()
        private const val INK_SOFT = 0xFF8E94AE.toInt()
    }
}
