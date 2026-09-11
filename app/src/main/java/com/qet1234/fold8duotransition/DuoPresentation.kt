package com.qet1234.fold8duotransition

import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.view.Display
import android.view.WindowManager

class DuoPresentation(
    context: Context,
    display: Display
) : Presentation(context, display) {

    lateinit var transitionView: DuoTransitionView
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        transitionView = DuoTransitionView(context).apply {
            setSide(1f)
        }
        setContentView(transitionView)
    }

    fun update(progress: Float, velocity: Float) {
        if (::transitionView.isInitialized) transitionView.setMotion(progress, velocity)
    }
}
