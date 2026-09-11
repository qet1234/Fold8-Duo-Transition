package com.qet1234.fold8duotransition

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs

/** Reads Android's standard hinge-angle sensor (TYPE_HINGE_ANGLE = 36). */
class HingeAngleSource(
    context: Context,
    private val onAngle: (rawDegrees: Float, progress01: Float) -> Unit,
    private val onUnavailable: () -> Unit
) : SensorEventListener {

    private val manager = context.getSystemService(SensorManager::class.java)
    private val hinge = manager.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)

    var closedAngle = 0f
    var openAngle = 180f

    private var smoothed: Float? = null

    fun start() {
        if (hinge == null) {
            onUnavailable()
            return
        }
        manager.registerListener(this, hinge, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() = manager.unregisterListener(this)

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_HINGE_ANGLE || event.values.isEmpty()) return
        val raw = event.values[0]

        val prev = smoothed
        val filtered = if (prev == null || abs(raw - prev) > 45f) raw else prev + (raw - prev) * 0.26f
        smoothed = filtered

        val span = (openAngle - closedAngle).takeIf { abs(it) > 1f } ?: 180f
        val progress = ((filtered - closedAngle) / span).coerceIn(0f, 1f)
        onAngle(filtered, progress)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
