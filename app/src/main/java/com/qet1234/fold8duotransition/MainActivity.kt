package com.qet1234.fold8duotransition

import android.app.Activity
import android.app.role.RoleManager
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var transitionView: DuoTransitionView
    private lateinit var launcherView: LauncherHomeView
    private lateinit var debugText: TextView
    private lateinit var homeRoleButton: Button
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

        transitionView = DuoTransitionView(this).apply { setSide(0f) }
        launcherView = LauncherHomeView(this)

        debugText = TextView(this).apply {
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0x88000000.toInt())
            textSize = 11f
            setPadding(18, 10, 18, 10)
            visibility = View.GONE
            setOnClickListener { visibility = View.GONE }
        }

        homeRoleButton = Button(this).apply {
            text = "기본 홈 앱으로 설정"
            textSize = 13f
            setAllCaps(false)
            setOnClickListener { requestHomeRole() }
        }

        val root = FrameLayout(this)
        root.addView(
            transitionView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        root.addView(
            launcherView,
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
            ).apply { setMargins(18, 18, 18, 18) }
        )
        root.addView(
            homeRoleButton,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            ).apply { bottomMargin = (116 * resources.displayMetrics.density).toInt() }
        )

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
                    lastProgress = if (resources.configuration.smallestScreenWidthDp >= 600) 1f else 0f
                    applyMotion(lastProgress, 0f)
                    debugText.visibility = View.VISIBLE
                    debugText.text = "Hinge sensor unavailable • launcher remains usable"
                }
            }
        )
    }

    override fun onResume() {
        super.onResume()
        launcherView.refreshApps()
        updateHomeRoleButton()
        hingeSource.start()
        applyMotion(lastProgress, lastVelocity)
    }

    override fun onPause() {
        hingeSource.stop()
        super.onPause()
    }

    private fun applyMotion(progress: Float, velocity: Float) {
        runCatching { transitionView.setMotion(progress, velocity) }
        runCatching { launcherView.setFoldMotion(progress, velocity) }
        refreshDebugText()
    }

    private fun requestHomeRole() {
        val roleManager = getSystemService(RoleManager::class.java)
        val launched = runCatching {
            if (roleManager.isRoleAvailable(RoleManager.ROLE_HOME) &&
                !roleManager.isRoleHeld(RoleManager.ROLE_HOME)
            ) {
                startActivityForResult(
                    roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME),
                    REQUEST_HOME_ROLE
                )
                true
            } else {
                false
            }
        }.getOrDefault(false)

        if (!launched) {
            runCatching { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
        }
    }

    private fun updateHomeRoleButton() {
        val held = runCatching {
            val roleManager = getSystemService(RoleManager::class.java)
            roleManager.isRoleAvailable(RoleManager.ROLE_HOME) &&
                roleManager.isRoleHeld(RoleManager.ROLE_HOME)
        }.getOrDefault(false)

        homeRoleButton.visibility = if (held) View.GONE else View.VISIBLE
    }

    @Deprecated("Kept for RoleManager request compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_HOME_ROLE) updateHomeRoleButton()
    }

    private fun refreshDebugText() {
        if (debugText.visibility != View.VISIBLE) return
        debugText.text = "hinge %.1f° • progress %.3f • velocity %+.0f°/s • %s"
            .format(lastAngle, lastProgress, lastVelocity, transitionView.renderModeLabel)
    }

    companion object {
        private const val REQUEST_HOME_ROLE = 7001
    }
}
