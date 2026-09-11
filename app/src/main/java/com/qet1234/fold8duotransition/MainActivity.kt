package com.qet1234.fold8duotransition

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var mainView: DuoTransitionView
    private lateinit var debugText: TextView
    private lateinit var hingeSource: HingeAngleSource

    private var lastProgress = 1f
    private var lastAngle = 180f
    private var lastVelocity = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        runCatching {
            window.insetsController?.hide(
                WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars()
            )
        }

        mainView = DuoTransitionView(this).apply { setSide(0f) }
        debugText = TextView(this).apply {
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0x88000000.toInt())
            textSize = 12f
            setPadding(20, 12, 20, 12)
            text = "Waiting for hinge sensor…"
            visibility = View.GONE
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

        root.setOnLongClickListener {
            debugText.visibility = if (debugText.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            refreshDebugText()
            true
        }

        setContentView(root)

        hingeSource = HingeAngleSource(
            context = this,
            onMotion = { raw, progress, velocity ->
                runOnUiThread {
                    lastAngle = raw
                    lastProgress = progress
                    lastVelocity = velocity
                    applyMotion(progress, velocity)
                }
            },
            onUnavailable = {
                runOnUiThread {
                    debugText.visibility = View.VISIBLE
                    debugText.text = "Hinge sensor unavailable • ${mainView.renderModeLabel}"
                }
            }
        )
    }

    override fun onResume() {
        super.onResume()
        hingeSource.start()
        applyMotion(lastProgress, lastVelocity)
    }

    override fun onPause() {
        hingeSource.stop()
        super.onPause()
    }

    private fun applyMotion(progress: Float, velocity: Float) {
        // Rendering failures are contained inside DuoTransitionView. The sensor callback
        // must never be able to take down the Activity while the device is folding.
        runCatching { mainView.setMotion(progress, velocity) }
        refreshDebugText()
    }

    private fun refreshDebugText() {
        if (debugText.visibility != View.VISIBLE) return
        debugText.text = "hinge %.1f°  •  progress %.3f  •  velocity %+.0f°/s  •  %s"
            .format(lastAngle, lastProgress, lastVelocity, mainView.renderModeLabel)
    }
}
