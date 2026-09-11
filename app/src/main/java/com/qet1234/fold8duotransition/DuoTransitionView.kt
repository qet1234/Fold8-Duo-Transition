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
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/**
 * High-fidelity hinge-driven fold transition POC.
 *
 * The view keeps the animation physically tied to the hinge sensor while the shader
 * adds depth cues: cylindrical pull near the hinge, perspective stretch, directional
 * motion blur, fold shadow, rim highlight and a hinge-led reveal of the destination.
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
    private var velocity = 0f
    private var side = 0f

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        runtimeShader.setInputShader("outerImage", outerShader)
        runtimeShader.setInputShader("innerImage", innerShader)
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
        runtimeShader.setFloatUniform("velocity", velocity)
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
            intArrayOf(0xFF0D2740.toInt(), 0xFF244C69.toInt(), 0xFF4D325F.toInt())
        } else {
            intArrayOf(0xFF24143D.toInt(), 0xFF664159.toInt(), 0xFF173B61.toInt())
        }
        p.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), colors, null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.shader = null

        // Soft top glow gives the shader something visible to bend around the hinge.
        p.shader = LinearGradient(
            0f,
            0f,
            0f,
            h * 0.42f,
            intArrayOf(0x28FFFFFF, 0x00FFFFFF),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w.toFloat(), h * 0.45f, p)
        p.shader = null

        val pad = w * 0.06f

        p.color = 0xEEFFFFFF.toInt()
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        p.textSize = w * if (inner) 0.075f else 0.105f
        canvas.drawText("12:28", pad, h * 0.105f, p)

        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        p.textSize = w * if (inner) 0.023f else 0.033f
        p.color = 0xCFFFFFFF.toInt()
        canvas.drawText("Friday, September 11", pad, h * 0.14f, p)

        val widgetTop = h * 0.18f
        val widgetH = h * if (inner) 0.18f else 0.16f
        p.color = 0x2FFFFFFF
        canvas.drawRoundRect(
            RectF(pad, widgetTop, w - pad, widgetTop + widgetH),
            w * 0.04f,
            w * 0.04f,
            p
        )
        p.color = 0xDFFFFFFF.toInt()
        p.textSize = w * if (inner) 0.027f else 0.038f
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(if (inner) "Expanded workspace" else "Cover home", pad * 1.55f, widgetTop + widgetH * 0.38f, p)
        p.color = 0xAFFFFFFF.toInt()
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        p.textSize *= 0.78f
        canvas.drawText("Hinge-reactive transition demo", pad * 1.55f, widgetTop + widgetH * 0.65f, p)

        val columns = if (inner) 6 else 4
        val rows = if (inner) 3 else 5
        val startY = h * 0.43f
        val usableW = w - pad * 2
        val stepX = usableW / columns
        val stepY = h * (if (inner) 0.16f else 0.105f)
        val radius = min(stepX, stepY) * 0.25f
        val iconColors = intArrayOf(
            0xFFEAF0FF.toInt(), 0xFFFFE9D7.toInt(), 0xFFDDF4F1.toInt(),
            0xFFFFE2EE.toInt(), 0xFFE2F5E7.toInt(), 0xFFDDEBFF.toInt()
        )
        for (r in 0 until rows) {
            for (c in 0 until columns) {
                val cx = pad + stepX * (c + 0.5f)
                val cy = startY + stepY * r
                p.color = iconColors[(r * columns + c) % iconColors.size]
                canvas.drawRoundRect(
                    RectF(cx - radius, cy - radius, cx + radius, cy + radius),
                    radius * 0.34f,
                    radius * 0.34f,
                    p
                )

                p.color = 0x26FFFFFF
                canvas.drawCircle(cx - radius * 0.22f, cy - radius * 0.22f, radius * 0.34f, p)
            }
        }

        p.color = 0x35FFFFFF
        val dockH = h * 0.078f
        val dockBottom = h - pad
        canvas.drawRoundRect(
            RectF(pad, dockBottom - dockH, w - pad, dockBottom),
            dockH * 0.42f,
            dockH * 0.42f,
            p
        )

        val dockCount = if (inner) 6 else 4
        val dockStep = (w - pad * 2) / dockCount
        val dockRadius = dockH * 0.24f
        for (i in 0 until dockCount) {
            val cx = pad + dockStep * (i + 0.5f)
            val cy = dockBottom - dockH * 0.5f
            p.color = iconColors[(i + 2) % iconColors.size]
            canvas.drawRoundRect(
                RectF(cx - dockRadius, cy - dockRadius, cx + dockRadius, cy + dockRadius),
                dockRadius * 0.35f,
                dockRadius * 0.35f,
                p
            )
        }

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

            half4 blurOuter(float2 p, float amount, float dir) {
                half4 c0 = outerImage.eval(p);
                half4 c1 = outerImage.eval(p + float2(dir * amount * 0.24, 0.0));
                half4 c2 = outerImage.eval(p - float2(dir * amount * 0.24, 0.0));
                half4 c3 = outerImage.eval(p + float2(dir * amount * 0.55, 0.0));
                half4 c4 = outerImage.eval(p - float2(dir * amount * 0.55, 0.0));
                half4 c5 = outerImage.eval(p + float2(dir * amount, 0.0));
                half4 c6 = outerImage.eval(p - float2(dir * amount, 0.0));
                return c0 * 0.28 + (c1 + c2) * 0.18 + (c3 + c4) * 0.11 + (c5 + c6) * 0.07;
            }

            half4 blurInner(float2 p, float amount, float dir) {
                half4 c0 = innerImage.eval(p);
                half4 c1 = innerImage.eval(p + float2(dir * amount * 0.24, 0.0));
                half4 c2 = innerImage.eval(p - float2(dir * amount * 0.24, 0.0));
                half4 c3 = innerImage.eval(p + float2(dir * amount * 0.55, 0.0));
                half4 c4 = innerImage.eval(p - float2(dir * amount * 0.55, 0.0));
                half4 c5 = innerImage.eval(p + float2(dir * amount, 0.0));
                half4 c6 = innerImage.eval(p - float2(dir * amount, 0.0));
                return c0 * 0.28 + (c1 + c2) * 0.18 + (c3 + c4) * 0.11 + (c5 + c6) * 0.07;
            }

            half4 main(float2 p) {
                float2 uv = p / resolution;
                float t = ease(clamp(progress, 0.0, 1.0));
                float mid = sin(t * 3.14159265);
                float speed = clamp(abs(velocity) / 520.0, 0.0, 1.0);

                // Each physical panel treats the edge nearest the hinge as distance 0.
                float hingeDistance = side < 0.5 ? (1.0 - uv.x) : uv.x;
                float direction = side < 0.5 ? 1.0 : -1.0;
                float hingeNear = exp(-hingeDistance * 7.0);
                float hingeTight = exp(-hingeDistance * 24.0);

                // Strongest deformation occurs half-way through the fold. Faster motion
                // increases the pull slightly without disconnecting it from the real angle.
                float fold = mid * (0.82 + speed * 0.18);
                float y = uv.y - 0.5;
                float barrel = 1.0 - clamp(y * y * 1.65, 0.0, 0.42);

                float pullPx = resolution.x * fold * hingeNear * barrel * (0.024 + 0.032 * speed);
                float perspectivePx = resolution.x * fold * hingeNear * hingeDistance * 0.050;
                float verticalPx = y * resolution.y * fold * hingeNear * 0.024;

                float2 outerP = p + float2(direction * (pullPx + perspectivePx), verticalPx);
                float2 innerP = p - float2(direction * (pullPx * 0.52 + perspectivePx * 0.28), verticalPx * 0.45);

                // Directional blur is intentionally speed-reactive. A slow fold remains
                // crisp; a fast fold gains the short smear seen in polished system UI.
                float blurPx = 0.75 + hingeNear * fold * (4.0 + speed * min(resolution.x, resolution.y) * 0.020);
                half4 fromColor = blurOuter(outerP, blurPx, direction);
                half4 toColor = blurInner(innerP, blurPx * 0.72, direction);

                // Destination content arrives from the hinge edge slightly before the far edge.
                float hingeLead = (1.0 - clamp(hingeDistance, 0.0, 1.0)) * 0.13;
                float blend = smoothstep(0.07, 0.93, t + hingeLead - 0.065);
                half4 color = mix(fromColor, toColor, blend);

                // Cylindrical shading sells depth far more than blur alone.
                float foldShadow = fold * hingeNear * (0.22 + speed * 0.07);
                float seamShadow = fold * hingeTight * 0.20;
                float vignette = fold * (0.045 + speed * 0.025) * (1.0 - 4.0 * y * y);
                float shade = 1.0 - foldShadow - seamShadow - max(vignette, 0.0);
                color.rgb *= half3(clamp(shade, 0.55, 1.0));

                // Thin moving specular rim at the hinge edge.
                float rim = exp(-abs(hingeDistance - 0.016) * 95.0) * fold * (0.08 + speed * 0.035);
                color.rgb += half3(rim);

                // Very subtle contrast lift through the midpoint keeps the scene from looking flat.
                float contrast = 1.0 + fold * 0.055;
                color.rgb = (color.rgb - half3(0.5)) * half3(contrast) + half3(0.5);

                return color;
            }
        """
    }
}
