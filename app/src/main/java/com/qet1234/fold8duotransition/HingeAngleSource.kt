package com.qet1234.fold8duotransition

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs
import kotlin.math.exp

/**
 * Reads Android's standard hinge-angle sensor (TYPE_HINGE_ANGLE = 36).
 *
 * In addition to angle/progress, this source estimates angular velocity so the
 * visual effect can react differently to a slow fold and a fast snap.
 */
class HingeAngleSource(
    context: Context,
    private val onMotion: (rawDegrees: Float, progress01: Float, velocityDegPerSecond: Float) -> Unit,
    private val onUnavailable: () -> Unit
) : SensorEventListener {

    private val manager = context.getSystemService(SensorManager::class.java)
    private val hinge = manager.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)

    var closedAngle = 0f
    var openAngle = 180f

    private var smoothedAngle: Float? = null
    private var smoothedVelocity = 0f
    private var lastTimestampNs = 0L

    fun start() {
        if (hinge == null) {
            onUnavailable()
            return
        }
        manager.registerListener(this, hinge, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        manager.unregisterListener(this)
        lastTimestampNs = 0L
        smoothedVelocity = 0f
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_HINGE_ANGLE || event.values.isEmpty()) return

        val raw = event.values[0]
        val nowNs = if (event.timestamp > 0L) event.timestamp else System.nanoTime()
        val dt = if (lastTimestampNs == 0L) {
            1f / 60f
        } else {
            ((nowNs - lastTimestampNs) / 1_000_000_000.0).toFloat().coerceIn(1f / 240f, 0.08f)
        }
        lastTimestampNs = nowNs

        val previous = smoothedAngle
        val angle = if (previous == null || abs(raw - previous) > 55f) {
            raw
        } else {
            // Time-based low-pass filter: responsive while remaining stable across
            // devices that report hinge events at different frequencies.
            val alpha = (1.0 - exp((-dt * 15f).toDouble())).toFloat().coerceIn(0.12f, 0.72f)
            previous + (raw - previous) * alpha
        }

        val instantVelocity = if (previous == null) 0f else (angle - previous) / dt
        smoothedVelocity += (instantVelocity - smoothedVelocity) * 0.22f
        smoothedAngle = angle

        val span = (openAngle - closedAngle).takeIf { abs(it) > 1f } ?: 180f
        val progress = ((angle - closedAngle) / span).coerceIn(0f, 1f)

        onMotion(angle, progress, smoothedVelocity.coerceIn(-720f, 720f))
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
