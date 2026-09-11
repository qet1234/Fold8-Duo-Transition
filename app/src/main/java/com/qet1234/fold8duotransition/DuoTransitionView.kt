package com.qet1234.fold8duotransition

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/**
 * Fold transition proof of concept.
 *
 * It intentionally uses generated home-screen mock bitmaps so the repository can
 * build immediately without requiring private screenshots. Replace createHomeMock()
 * with real cover/inner screenshots after the hinge behavior is verified on-device.
 */
class DuoTransitionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val outerBitmap: Bitmap = createHomeMock(1080, 2520, false)
    private val innerBitmap: Bitmap = createHomeMock(2208, 1840, true)

    private val outerShader = BitmapShader(outerBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    private val innerShader = BitmapShader(innerBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)

    private val runtimeShader = RuntimeShader(AGSL)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = runtimeShader }

    private var progress = 1f
    private var side = 0f

    init {
        runtimeShader.setInputShader("outerImage", outerShader)
        runtimeShader.setInputShader("innerImage", innerShader)
    }

    fun setProgress(value: Float) {
        progress = value.coerceIn(0f, 1f)
        invalidate()
    }

    fun setSide(value: Float) {
        side = value.coerceIn(0f, 1f)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        outerShader.setLocalMatrix(centerCropMatrix(outerBitmap, w, h))
        innerShader.setLocalMatrix(centerCropMatrix(innerBitmap, w, h))
        runtimeShader.setInputShader("outerImage", outerShader)
        runtimeShader.setInputShader("innerImage", innerShader)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        runtimeShader.setFloatUniform("resolution", width.toFloat(), height.toFloat())
        runtimeShader.setFloatUniform("progress", progress)
        runtimeShader.setFloatUniform("side", side)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    private fun centerCropMatrix(bitmap: Bitmap, viewW: Int, viewH: Int): Matrix {
        val bw = bitmap.width.toFloat()
        val bh = bitmap.height.toFloat()
        val scale = maxOf(viewW / bw, viewH / bh)
        val dx = (viewW - bw * scale) * 0.5f
        val dy = (viewH - bh * scale) * 0.5f
        return Matrix().apply {
            setScale(scale, scale)
            postTranslate(dx, dy)
        }
    }

    private fun createHomeMock(w: Int, h: Int, inner: Boolean): Bitmap {
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        val colors = if (inner) {
            intArrayOf(0xFF12385B.toInt(), 0xFF2D5570.toInt(), 0xFF402E62.toInt())
        } else {
            intArrayOf(0xFF361B52.toInt(), 0xFF714763.toInt(), 0xFF203D62.toInt())
        }
        p.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), colors, null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.shader = null

        val pad = w * 0.065f
        val widgetTop = h * 0.09f
        val widgetH = h * 0.16f
        p.color = 0x33FFFFFF
        canvas.drawRoundRect(RectF(pad, widgetTop, w - pad, widgetTop + widgetH), w * 0.045f, w * 0.045f, p)

        val columns = if (inner) 6 else 4
        val rows = if (inner) 3 else 5
        val startY = h * 0.36f
        val usableW = w - pad * 2
        val stepX = usableW / columns
        val stepY = h * (if (inner) 0.17f else 0.11f)
        val radius = min(stepX, stepY) * 0.23f
        val iconColors = intArrayOf(
            0xFFE8EAF6.toInt(), 0xFFFFF3E0.toInt(), 0xFFE0F2F1.toInt(),
            0xFFFCE4EC.toInt(), 0xFFE8F5E9.toInt(), 0xFFE3F2FD.toInt()
        )
        for (r in 0 until rows) {
            for (c in 0 until columns) {
                val cx = pad + stepX * (c + 0.5f)
                val cy = startY + stepY * r
                p.color = iconColors[(r * columns + c) % iconColors.size]
                canvas.drawRoundRect(RectF(cx - radius, cy - radius, cx + radius, cy + radius), radius * 0.32f, radius * 0.32f, p)
            }
        }

        p.color = 0x44FFFFFF
        val dockH = h * 0.075f
        canvas.drawRoundRect(RectF(pad, h - dockH - pad, w - pad, h - pad), dockH * 0.38f, dockH * 0.38f, p)
        return bitmap
    }

    companion object {
        private const val AGSL = """
            uniform shader outerImage;
            uniform shader innerImage;
            uniform float2 resolution;
            uniform float progress;
            uniform float side;

            half4 sampleOuter(float2 p, float amount, float dir) {
                half4 c0 = outerImage.eval(p);
                half4 c1 = outerImage.eval(p + float2(dir * amount * 0.35, 0.0));
                half4 c2 = outerImage.eval(p - float2(dir * amount * 0.35, 0.0));
                half4 c3 = outerImage.eval(p + float2(dir * amount * 0.75, 0.0));
                half4 c4 = outerImage.eval(p - float2(dir * amount * 0.75, 0.0));
                return c0 * 0.34 + (c1 + c2) * 0.20 + (c3 + c4) * 0.13;
            }

            half4 sampleInner(float2 p, float amount, float dir) {
                half4 c0 = innerImage.eval(p);
                half4 c1 = innerImage.eval(p + float2(dir * amount * 0.35, 0.0));
                half4 c2 = innerImage.eval(p - float2(dir * amount * 0.35, 0.0));
                half4 c3 = innerImage.eval(p + float2(dir * amount * 0.75, 0.0));
                half4 c4 = innerImage.eval(p - float2(dir * amount * 0.75, 0.0));
                return c0 * 0.34 + (c1 + c2) * 0.20 + (c3 + c4) * 0.13;
            }

            half4 main(float2 p) {
                float2 uv = p / resolution;
                float t = smoothstep(0.015, 0.985, progress);
                float motion = sin(t * 3.14159265);

                float hingeDistance = side < 0.5 ? (1.0 - uv.x) : uv.x;
                float hingeInfluence = exp(-hingeDistance * 8.5);
                float direction = side < 0.5 ? 1.0 : -1.0;

                float warpPx = motion * hingeInfluence * resolution.x * 0.032;
                float2 outerP = p + float2(direction * warpPx, 0.0);
                float2 innerP = p - float2(direction * warpPx * 0.55, 0.0);

                float blurPx = 1.0 + motion * hingeInfluence * min(resolution.x, resolution.y) * 0.018;
                half4 a = sampleOuter(outerP, blurPx, direction);
                half4 b = sampleInner(innerP, blurPx, direction);

                float spatialLead = (1.0 - clamp(hingeDistance * 1.15, 0.0, 1.0)) * 0.10;
                float blend = smoothstep(0.06, 0.94, t + spatialLead - 0.04);

                float shade = 1.0 - motion * hingeInfluence * 0.20;
                half4 outColor = mix(a, b, blend);
                outColor.rgb *= half3(shade);
                return outColor;
            }
        """
    }
}
