package com.qet1234.fold8duotransition

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import android.view.View
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

/**
 * A real, usable launcher surface.
 *
 * Installed launchable activities are queried from PackageManager and rendered as
 * tappable icons. The same app index is mapped into a 4-column cover layout and a
 * 6-column inner layout; hinge progress interpolates between those two positions so
 * content appears spatially continuous while the Fold is opening/closing.
 */
class LauncherHomeView(context: Context) : View(context) {

    private data class AppEntry(
        val label: String,
        val packageName: String,
        val activityName: String,
        val icon: Drawable
    )

    private data class HitTarget(val rect: RectF, val app: AppEntry)

    private val apps = ArrayList<AppEntry>()
    private val hitTargets = ArrayList<HitTarget>()

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xF2FFFFFF.toInt()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }
    private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xCCFFFFFF.toInt()
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }
    private val dockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x28FFFFFF
    }

    private var progress = 1f
    private var velocity = 0f
    private var scrollOffset = 0f
    private var maxScroll = 0f

    private var downX = 0f
    private var downY = 0f
    private var lastY = 0f
    private var dragging = false

    private val density = resources.displayMetrics.density
    private val touchSlop = 8f * density

    init {
        isFocusable = true
        isClickable = true
        refreshApps()
    }

    fun refreshApps() {
        val pm = context.packageManager
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved: List<ResolveInfo> = runCatching {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(query, 0)
        }.getOrDefault(emptyList())

        apps.clear()
        resolved.asSequence()
            .filter { it.activityInfo?.packageName != context.packageName }
            .mapNotNull { info ->
                val activity = info.activityInfo ?: return@mapNotNull null
                val label = runCatching { info.loadLabel(pm).toString() }
                    .getOrDefault(activity.packageName)
                val icon = runCatching { info.loadIcon(pm) }.getOrNull() ?: return@mapNotNull null
                AppEntry(label, activity.packageName, activity.name, icon)
            }
            .distinctBy { "${it.packageName}/${it.activityName}" }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
            .forEach(apps::add)

        scrollOffset = scrollOffset.coerceAtLeast(0f)
        invalidate()
    }

    fun setFoldMotion(value: Float, velocityDegPerSecond: Float) {
        progress = value.coerceIn(0f, 1f)
        velocity = velocityDegPerSecond.coerceIn(-720f, 720f)

        val mid = sin(progress * PI).toFloat().coerceAtLeast(0f)
        val speed = (abs(velocity) / 520f).coerceIn(0f, 1f)
        val blur = mid * (2.2f + 7.5f * speed)
        setRenderEffect(
            if (blur > 0.6f) {
                RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.CLAMP)
            } else {
                null
            }
        )

        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        hitTargets.clear()

        val t = progress * progress * (3f - 2f * progress)
        val mid = sin(t * PI).toFloat().coerceAtLeast(0f)
        val w = width.toFloat()
        val h = height.toFloat()

        val sidePad = 22f * density
        val top = if (h > w * 1.45f) 178f * density else 128f * density
        val rowHeight = lerp(108f * density, 96f * density, t)
        val iconSize = lerp(minOf(w / 5.6f, 70f * density), minOf(w / 7.8f, 66f * density), t)

        drawHeader(canvas, sidePad)

        // Four stable dock apps remain at the bottom in both layouts.
        val dockHeight = 82f * density
        val dockBottom = h - 22f * density
        val dockTop = dockBottom - dockHeight
        canvas.drawRoundRect(
            RectF(sidePad, dockTop, w - sidePad, dockBottom),
            30f * density,
            30f * density,
            dockPaint
        )

        val dockCount = minOf(4, apps.size)
        if (dockCount > 0) {
            val dockStep = (w - sidePad * 2f) / dockCount
            for (i in 0 until dockCount) {
                val app = apps[i]
                val cx = sidePad + dockStep * (i + 0.5f)
                val cy = dockTop + dockHeight * 0.5f
                drawApp(canvas, app, cx, cy, iconSize * 0.82f, false, mid)
            }
        }

        val gridApps = if (apps.size > dockCount) apps.subList(dockCount, apps.size) else emptyList()
        val gridBottom = dockTop - 20f * density

        val closedRows = if (gridApps.isEmpty()) 0 else (gridApps.size + 3) / 4
        val openRows = if (gridApps.isEmpty()) 0 else (gridApps.size + 5) / 6
        val interpolatedRows = lerp(closedRows.toFloat(), openRows.toFloat(), t)
        val contentHeight = top + interpolatedRows * rowHeight + 48f * density
        maxScroll = max(0f, contentHeight - gridBottom)
        scrollOffset = scrollOffset.coerceIn(0f, maxScroll)

        for (i in gridApps.indices) {
            val app = gridApps[i]

            val closedCol = i % 4
            val closedRow = i / 4
            val openCol = i % 6
            val openRow = i / 6

            val closedCellW = w / 4f
            val openCellW = w / 6f

            val closedX = closedCellW * (closedCol + 0.5f)
            val openX = openCellW * (openCol + 0.5f)
            val closedY = top + closedRow * rowHeight - scrollOffset
            val openY = top + openRow * rowHeight - scrollOffset

            // The layout morphs instead of snapping when Android swaps from the cover
            // display geometry to the inner display geometry.
            val x = lerp(closedX, openX, t)
            val y = lerp(closedY, openY, t)

            if (y < top - rowHeight || y > gridBottom + rowHeight) continue
            drawApp(canvas, app, x, y, iconSize, true, mid)
        }

        // Keep the clock fresh while this view remains the active HOME activity.
        postInvalidateDelayed(30_000L)
    }

    private fun drawHeader(canvas: Canvas, sidePad: Float) {
        val now = Date()
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
        val date = SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(now)

        timePaint.textSize = if (width < height * 0.72f) 38f * density else 32f * density
        datePaint.textSize = 14f * density
        canvas.drawText(time, sidePad, 58f * density, timePaint)
        canvas.drawText(date, sidePad, 82f * density, datePaint)

        datePaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Duo Home", width - sidePad, 58f * density, datePaint)
        datePaint.textAlign = Paint.Align.LEFT
    }

    private fun drawApp(
        canvas: Canvas,
        app: AppEntry,
        cx: Float,
        cy: Float,
        iconSize: Float,
        showLabel: Boolean,
        foldMidpoint: Float
    ) {
        val iconTop = cy - iconSize * 0.5f
        val rect = RectF(
            cx - iconSize * 0.5f,
            iconTop,
            cx + iconSize * 0.5f,
            iconTop + iconSize
        )

        val drawable = app.icon
        drawable.alpha = (255f * (1f - foldMidpoint * 0.08f)).toInt().coerceIn(210, 255)
        drawable.setBounds(rect.left.toInt(), rect.top.toInt(), rect.right.toInt(), rect.bottom.toInt())
        runCatching { drawable.draw(canvas) }
        drawable.alpha = 255

        if (showLabel) {
            textPaint.textSize = 11f * density
            val maxChars = if (progress > 0.55f) 13 else 10
            val label = if (app.label.length > maxChars) app.label.take(maxChars - 1) + "…" else app.label
            canvas.drawText(label, cx, rect.bottom + 18f * density, textPaint)
        }

        val hitPad = 14f * density
        hitTargets += HitTarget(
            RectF(rect.left - hitPad, rect.top - hitPad, rect.right + hitPad, rect.bottom + 28f * density),
            app
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                lastY = event.y
                dragging = false
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dy = event.y - lastY
                if (!dragging && (abs(event.y - downY) > touchSlop || abs(event.x - downX) > touchSlop)) {
                    dragging = true
                }
                if (dragging) {
                    scrollOffset = (scrollOffset - dy).coerceIn(0f, maxScroll)
                    invalidate()
                }
                lastY = event.y
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (!dragging && (progress < 0.12f || progress > 0.88f)) {
                    hitTargets.asReversed().firstOrNull { it.rect.contains(event.x, event.y) }?.let {
                        launch(it.app)
                    }
                }
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun launch(app: AppEntry) {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(ComponentName(app.packageName, app.activityName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        runCatching { context.startActivity(intent) }
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
}
