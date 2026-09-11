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

/**
 * Fold-reactive wallpaper/backdrop renderer.
 *
 * Real launcher icons are rendered by LauncherHomeView. This view intentionally draws
 * only the animated background so the user never sees duplicate/fake icons during a
 * fold. AGSL remains optional and automatically falls back to Canvas on GPU failure.
 */
class DuoTransitionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val outerBitmap = createBackdrop(360, 840, false)
    private val innerBitmap = createBackdrop(840, 700, true)

    private val outerShader = BitmapShader(outerBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    private val innerShader = BitmapShader(innerBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)

    private var shaderFailed = false
    private val runtimeShader: RuntimeShader? = runCatching { RuntimeShader(AGSL) }
        .onFailure { shaderFailed = true }
        .getOrNull()

    private val shaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = runtimeShader
        isDither = true
    }
    private val fallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val seamPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var progress = 1f
    private var velocity = 0f
    private var side = 0f

    val renderModeLabel: String
        get() = if (runtimeShader != null && !shaderFailed) "AGSL" else "Safe Canvas"

    init {
        runtimeShader?.let { shader ->
            runCatching {
                shader.setInputShader("outerImage", outerShader)
                shader.setInputShader("innerImage", innerShader)
            }.onFailure { shaderFailed = true }
        }
    }

    fun setProgress(value: Float) = setMotion(value, 0f)

    fun setMotion(value: Float, velocityDegPerSecond: Float) {
        progress = value.coerceIn(0f, 1f)
        velocity = velocityDegPerSecond.coerceIn(-720f, 720f)
        invalidate()
    }

    fun setSide(value: Float) {
        side = value.coerceIn(0f, 1f)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        runCatching {
            outerShader.setLocalMatrix(centerCropMatrix(outerBitmap, w, h))
            innerShader.setLocalMatrix(centerCropMatrix(innerBitmap, w, h))
            runtimeShader?.setInputShader("outerImage", outerShader)
            runtimeShader?.setInputShader("innerImage", innerShader)
        }.onFailure { shaderFailed = true }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        val shader = runtimeShader
        if (shader != null && !shaderFailed) {
            val success = runCatching {
                shader.setFloatUniform("resolution", width.toFloat(), height.toFloat())
                shader.setFloatUniform("progress", progress)
                shader.setFloatUniform("velocity", velocity)
                shader.setFloatUniform("side", side)
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), shaderPaint)
            }.isSuccess
            if (success) return
            shaderFailed = true
        }

        drawFallback(canvas)
    }

    private fun drawFallback(canvas: Canvas) {
        val t = progress * progress * (3f - 2f * progress)
        val fold = kotlin.math.sin(t * Math.PI).toFloat().coerceAtLeast(0f)
        val dst = RectF(0f, 0f, width.toFloat(), height.toFloat())

        fallbackPaint.alpha = ((1f - t) * 255f).toInt().coerceIn(0, 255)
        canvas.drawBitmap(outerBitmap, null, dst, fallbackPaint)

        fallbackPaint.alpha = (t * 255f).toInt().coerceIn(0, 255)
        canvas.drawBitmap(innerBitmap, null, dst, fallbackPaint)

        val seamWidth = width * (0.025f + fold * 0.035f)
        val hingeX = if (side < 0.5f) width.toFloat() else 0f
        val left = if (side < 0.5f) hingeX - seamWidth else hingeX
        val right = if (side < 0.5f) hingeX else hingeX + seamWidth
        seamPaint.shader = LinearGradient(
            left,
            0f,
            right,
            0f,
            if (side < 0.5f) {
                intArrayOf(0x00101010, 0xA0101010.toInt())
            } else {
                intArrayOf(0xA0101010.toInt(), 0x00101010)
            },
            null,
            Shader.TileMode.CLAMP
        )
        seamPaint.alpha = (fold * 210f).toInt().coerceIn(0, 210)
        canvas.drawRect(left, 0f, right, height.toFloat(), seamPaint)
        seamPaint.shader = null
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

    private fun createBackdrop(w: Int, h: Int, inner: Boolean): Bitmap {
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val colors = if (inner) {
            intArrayOf(0xFF091F36.toInt(), 0xFF1F4966.toInt(), 0xFF513662.toInt())
        } else {
            intArrayOf(0xFF211238.toInt(), 0xFF68445C.toInt(), 0xFF123A62.toInt())
        }
        paint.shader = LinearGradient(
            0f,
            0f,
            w.toFloat(),
            h.toFloat(),
            colors,
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)

        paint.shader = LinearGradient(
            0f,
            0f,
            0f,
            h * 0.62f,
            intArrayOf(0x28FFFFFF, 0x00FFFFFF),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w.toFloat(), h * 0.68f, paint)
        paint.shader = null
        return bitmap
    }

    companion object {
        private const val AGSL = """
            uniform shader outerImage;
            uniform shader innerImage;
            uniform float2 resolution;
            uniform float progress;
            uniform float velocity;
            uniform float side;

            float ease(float x) {
                return x * x * (3.0 - 2.0 * x);
            }

            half4 sampleOuter(float2 p, float blurPx, float dir) {
                half4 c0 = outerImage.eval(p);
                half4 c1 = outerImage.eval(p + float2(dir * blurPx * 0.45, 0.0));
                half4 c2 = outerImage.eval(p - float2(dir * blurPx * 0.45, 0.0));
                return c0 * 0.56 + (c1 + c2) * 0.22;
            }

            half4 sampleInner(float2 p, float blurPx, float dir) {
                half4 c0 = innerImage.eval(p);
                half4 c1 = innerImage.eval(p + float2(dir * blurPx * 0.45, 0.0));
                half4 c2 = innerImage.eval(p - float2(dir * blurPx * 0.45, 0.0));
                return c0 * 0.56 + (c1 + c2) * 0.22;
            }

            half4 main(float2 p) {
                float2 uv = p / resolution;
                float t = ease(clamp(progress, 0.0, 1.0));
                float mid = sin(t * 3.14159265);
                float speed = clamp(abs(velocity) / 520.0, 0.0, 1.0);

                float hingeDistance = side < 0.5 ? (1.0 - uv.x) : uv.x;
                float direction = side < 0.5 ? 1.0 : -1.0;
                float nearHinge = exp(-hingeDistance * 7.2);
                float tightHinge = exp(-hingeDistance * 26.0);

                float fold = mid * (0.84 + speed * 0.16);
                float y = uv.y - 0.5;
                float curve = 1.0 - clamp(y * y * 1.45, 0.0, 0.40);
                float pull = resolution.x * nearHinge * fold * curve * (0.018 + speed * 0.022);

                float2 outerP = p + float2(direction * pull, y * resolution.y * nearHinge * fold * 0.012);
                float2 innerP = p - float2(direction * pull * 0.44, y * resolution.y * nearHinge * fold * 0.006);

                float blurPx = 0.7 + nearHinge * fold * (3.0 + speed * min(resolution.x, resolution.y) * 0.012);
                half4 a = sampleOuter(outerP, blurPx, direction);
                half4 b = sampleInner(innerP, blurPx * 0.75, direction);

                float lead = (1.0 - clamp(hingeDistance, 0.0, 1.0)) * 0.10;
                float blend = smoothstep(0.08, 0.92, t + lead - 0.05);
                half4 color = mix(a, b, blend);

                float shadow = fold * nearHinge * 0.18 + fold * tightHinge * 0.16;
                color.rgb *= half3(clamp(1.0 - shadow, 0.62, 1.0));

                float rim = exp(-abs(hingeDistance - 0.015) * 100.0) * fold * 0.07;
                color.rgb += half3(rim);
                return color;
            }
        """
    }
}
