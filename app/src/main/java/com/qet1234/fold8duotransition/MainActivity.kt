package com.qet1234.fold8duotransition

import android.app.Activity
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.view.Display
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import java.util.concurrent.CopyOnWriteArrayList

class MainActivity : Activity(), DisplayManager.DisplayListener {

    private lateinit var mainView: DuoTransitionView
    private lateinit var debugText: TextView
    private lateinit var hingeSource: HingeAngleSource
    private lateinit var displayManager: DisplayManager

    private val presentations = CopyOnWriteArrayList<DuoPresentation>()
    private var lastProgress = 1f
    private var lastAngle = 180f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.insetsController?.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())

        mainView = DuoTransitionView(this).apply { setSide(0f) }
        debugText = TextView(this).apply {
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0x66000000)
            textSize = 12f
            setPadding(20, 12, 20, 12)
            text = "Waiting for hinge sensor…"
        }

        val root = FrameLayout(this)
        root.addView(
            mainView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        root.addView(
            debugText,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.START
            ).apply { setMargins(24, 24, 24, 24) }
        )
        setContentView(root)

        displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        displayManager.registerDisplayListener(this, null)

        hingeSource = HingeAngleSource(
            context = this,
            onAngle = { raw, progress ->
                runOnUiThread {
                    lastAngle = raw
                    lastProgress = progress
                    applyProgress(raw, progress)
                }
            },
            onUnavailable = {
                runOnUiThread {
                    debugText.text = "TYPE_HINGE_ANGLE unavailable on this device"
                }
            }
        )

        refreshPresentations()
    }

    override fun onResume() {
        super.onResume()
        hingeSource.start()
        refreshPresentations()
    }

    override fun onPause() {
        hingeSource.stop()
        super.onPause()
    }

    override fun onDestroy() {
        displayManager.unregisterDisplayListener(this)
        presentations.forEach { runCatching { it.dismiss() } }
        presentations.clear()
        super.onDestroy()
    }

    private fun applyProgress(rawAngle: Float, progress: Float) {
        mainView.setProgress(progress)
        presentations.forEach { it.update(progress) }
        debugText.text = "hinge %.1f°  •  progress %.3f  •  presentations %d"
            .format(rawAngle, progress, presentations.size)
    }

    private fun refreshPresentations() {
        val currentId = display?.displayId ?: Display.DEFAULT_DISPLAY
        val available = displayManager.displays
            .filter { it.displayId != currentId && it.state != Display.STATE_OFF }

        val availableIds = available.map { it.displayId }.toSet()
        presentations.filter { it.display.displayId !in availableIds }.forEach {
            runCatching { it.dismiss() }
            presentations.remove(it)
        }

        val existingIds = presentations.map { it.display.displayId }.toSet()
        available.filter { it.displayId !in existingIds }.forEach { target ->
            runCatching {
                DuoPresentation(this, target).also { presentation ->
                    presentation.show()
                    presentation.update(lastProgress)
                    presentations += presentation
                }
            }
        }

        applyProgress(lastAngle, lastProgress)
    }

    override fun onDisplayAdded(displayId: Int) = refreshPresentations()
    override fun onDisplayRemoved(displayId: Int) = refreshPresentations()
    override fun onDisplayChanged(displayId: Int) = refreshPresentations()
}
