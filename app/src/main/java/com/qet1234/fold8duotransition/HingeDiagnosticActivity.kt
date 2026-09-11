package com.qet1234.fold8duotransition

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

/** Read-only diagnostic. It never requests the HOME role or invents sensor values. */
class HingeDiagnosticActivity : Activity(), SensorEventListener {
    private lateinit var manager: SensorManager
    private var selected: Sensor? = null
    private var candidates: List<Sensor> = emptyList()
    private var candidateIndex = 0
    private var registered = false
    private var registrationError: String? = null
    private var accuracy: Int? = null
    private var stats = HingeSamples()
    private var lastReceivedAt = 0L
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var angleText: TextView
    private lateinit var statusText: TextView
    private lateinit var detailsText: TextView
    private lateinit var graph: AngleGraph
    private lateinit var sensorButton: Button
    private lateinit var deviceText: TextView
    private val ticker = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 100)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        manager = getSystemService(SensorManager::class.java)
        candidates = manager.getSensorList(Sensor.TYPE_HINGE_ANGLE)
        selected = manager.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE) ?: candidates.firstOrNull()
        candidateIndex = candidates.indexOf(selected).coerceAtLeast(0)
        // Keep the previous session readable after process recreation. New measurements
        // always form a new session so pauses cannot masquerade as sensor sample gaps.
        val previousReport = getPreferences(MODE_PRIVATE).getString("last_report", null)
        val scroll = ScrollView(this).apply { setBackgroundColor(0xFF101724.toInt()); isFillViewport = true }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(20), dp(24), dp(24))
        }
        scroll.addView(column)
        scroll.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        fun text(value: String, size: Float): TextView = TextView(this).apply {
            this.text = value; textSize = size; setTextColor(0xFFF1F5FF.toInt())
            setPadding(0, dp(8), 0, dp(8)); column.addView(this)
        }
        text("힌지 센서 진단", 28f)
        text("폴드8 울트라 · 실제 측정", 16f)
        deviceText = text(deviceDescription(), 13f)
        text("화면을 켠 채 천천히 접었다 펼쳐 보세요. 중간 각도에서 잠시 멈춘 뒤 반대 방향으로 움직여 주세요. 기본 홈 설정은 필요 없습니다.", 15f)
        statusText = text("센서 확인 중", 16f)
        angleText = text("—°", 52f)
        angleText.contentDescription = "측정된 힌지 각도 없음"
        graph = AngleGraph().also { column.addView(it, LinearLayout.LayoutParams(-1, dp(160))) }
        detailsText = text("측정값 대기", 14f)
        fun button(label: String, action: () -> Unit): Button = Button(this).apply {
            this.text = label; isAllCaps = false; minHeight = dp(48)
            setOnClickListener { action() }; column.addView(this, LinearLayout.LayoutParams(-1, -2))
        }
        sensorButton = button("센서 바꾸기") {
            stopSensor()
            candidateIndex = (candidateIndex + 1) % candidates.size
            selected = candidates[candidateIndex]
            reset(); startSensor()
        }
        sensorButton.visibility = if (candidates.size > 1) View.VISIBLE else View.GONE
        button("측정 다시 시작") { stopSensor(); reset(); startSensor() }
        button("결과 복사") { copyReport(report()) }
        button("결과 공유") { shareReport(report()) }
        if (previousReport != null) button("이전 실행 결과 복사") { copyReport(previousReport) }
        text("각도 그래프는 최근 측정값을 표시합니다. 움직이지 않을 때 새 값이 오지 않는 센서도 있습니다. 이 결과만으로 정밀도나 모든 각도의 지원을 확정하지 않습니다.", 12f)
        text("자동 전송 없음 · 공유할 때 기기 모델/OS, 센서 정보와 측정 각도만 포함됩니다.", 12f)
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        startSensor()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    override fun onPause() {
        stopSensor()
        handler.removeCallbacks(ticker)
        getPreferences(MODE_PRIVATE).edit().putString("last_report", report()).apply()
        super.onPause()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        deviceText.text = deviceDescription()
    }

    private fun startSensor() {
        if (registered) return
        registrationError = null
        val sensor = selected ?: return
        stats.beginSegment()
        registered = try {
            manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        } catch (e: RuntimeException) {
            registrationError = e.javaClass.simpleName
            false
        }
        if (!registered && registrationError == null) registrationError = "registerListener returned false"
    }

    private fun stopSensor() {
        manager.unregisterListener(this)
        registered = false
        stats.beginSegment()
    }

    private fun reset() {
        stats = HingeSamples()
        accuracy = null
        lastReceivedAt = 0L
        refresh()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor != selected || event.values.isEmpty()) return
        if (stats.add(event.values[0], event.timestamp)) lastReceivedAt = SystemClock.elapsedRealtime()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (sensor == selected) this.accuracy = accuracy
    }

    private fun status(): String = when {
        selected == null -> "표준 힌지 센서가 앱에 제공되지 않음"
        registrationError != null -> "센서 등록 실패: $registrationError"
        stats.count == 0 -> "센서 발견 · 측정값 대기 중"
        !registered -> "측정 일시 정지 · 마지막 결과 표시"
        SystemClock.elapsedRealtime() - lastReceivedAt > 8000 -> "마지막 측정값 유지 · 움직여서 확인해 주세요"
        else -> "실제 힌지 각도 수신 중"
    }

    private fun refresh() {
        statusText.text = status()
        angleText.text = stats.last?.let { fmt(it) + "°" } ?: "—°"
        angleText.contentDescription = "측정된 힌지 각도 ${angleText.text}"
        detailsText.text = buildString {
            append("수신 ${stats.count}회 · 서로 다른 각도 ${stats.distinctCount}개\n")
            append("관측 범위 ${stats.minimum?.let(::fmt) ?: "—"}° ~ ${stats.maximum?.let(::fmt) ?: "—"}°\n")
            append("${selected?.name ?: "표준 센서 없음"}\n")
            append("측정값은 보정·평활화하지 않은 원본입니다.")
        }
        graph.invalidate()
    }

    private fun deviceDescription(): String {
        val metrics = windowManager.currentWindowMetrics.bounds
        return "${Build.MANUFACTURER} / ${Build.MODEL}\nAndroid ${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}\n현재 창 ${metrics.width()} × ${metrics.height()} px"
    }

    private fun report(): String = buildString {
        appendLine("Duo Home — Hinge diagnostics ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Target selected by user: Galaxy Z Fold8 Ultra (model compatibility unverified)")
        appendLine(deviceDescription())
        appendLine("Status: ${status()}")
        appendLine("Standard hinge sensors: ${candidates.size}")
        candidates.forEachIndexed { index, s ->
            appendLine("Sensor[$index]: ${s.name}; vendor=${s.vendor}; type=${s.type}; version=${s.version}; max=${s.maximumRange}; resolution=${s.resolution}; reportingMode=${s.reportingMode}; wakeUp=${s.isWakeUpSensor}")
        }
        appendLine("Selected: ${selected?.name ?: "none"}; accuracy=$accuracy")
        appendLine("Accepted=${stats.count}; distinct=${stats.distinctCount}; rejected=${stats.rejected}")
        appendLine("Observed min=${stats.minimum}; max=${stats.maximum}; last=${stats.last}")
        appendLine("Observed endpoints are not calibrated physical endpoints.")
        appendLine("No events at rest may be normal. Test slow folding, pauses and reversal on device.")
        appendLine("Recent raw samples (up to 200): segment,sensor_timestamp_ns,angle_degrees")
        stats.samples.takeLast(200).forEach { appendLine("${it.segment},${it.timestampNs},${it.angle}") }
    }

    private fun copyReport(value: String) {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Hinge diagnostics", value))
        Toast.makeText(this, "결과를 복사했습니다. 채팅에 붙여 넣어 주세요.", Toast.LENGTH_LONG).show()
    }

    private fun shareReport(value: String) {
        try {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"; putExtra(Intent.EXTRA_SUBJECT, "힌지 센서 진단 결과")
                putExtra(Intent.EXTRA_TEXT, value)
            }, "진단 결과 공유"))
        } catch (_: android.content.ActivityNotFoundException) {
            copyReport(value)
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun fmt(value: Float) = String.format(Locale.US, "%.1f", value)

    private inner class AngleGraph : View(this@HingeDiagnosticActivity) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val left = dp(42).toFloat(); val right = width - dp(8).toFloat()
            val top = dp(16).toFloat(); val bottom = height - dp(26).toFloat()
            if (right <= left || bottom <= top) return
            val points = stats.samples.takeLast(120)
            val maxAngle = if ((stats.maximum ?: 0f) > 180f) 360f else 180f
            paint.textSize = dp(11).toFloat(); paint.strokeWidth = dp(1).toFloat()
            for (i in 0..2) {
                val y = top + (bottom - top) * i / 2
                paint.color = 0xFF344155.toInt()
                canvas.drawLine(left, y, right, y, paint)
                paint.color = 0xFFACBBD0.toInt()
                canvas.drawText("${(maxAngle * (2-i) / 2).toInt()}°", 0f, y + dp(4), paint)
            }
            paint.color = 0xFFACBBD0.toInt()
            canvas.drawText("최근 수신 순서 (시간 간격은 일정하지 않음)", left, height - dp(4).toFloat(), paint)
            paint.color = 0xFF7AE6CF.toInt(); paint.strokeWidth = dp(2).toFloat()
            val path = Path()
            points.forEachIndexed { i, p ->
                val x = left + (right-left) * i / maxOf(points.size-1, 1)
                val y = bottom - (p.angle / maxAngle) * (bottom-top)
                if (i == 0 || p.segment != points[i-1].segment) path.moveTo(x,y) else path.lineTo(x,y)
                canvas.drawCircle(x, y, dp(2).toFloat(), paint)
            }
            paint.style = Paint.Style.STROKE; canvas.drawPath(path, paint); paint.style = Paint.Style.FILL
        }
    }
}
