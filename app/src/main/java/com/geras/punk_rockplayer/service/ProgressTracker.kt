package com.geras.punk_rockplayer.service

import android.os.Handler
import android.os.Looper
import com.google.android.exoplayer2.Player

class ProgressTracker(private val player: Player, private val positionListener: PositionListener) :
    Runnable {

    private val handler: Handler = Handler(Looper.getMainLooper())
    private var isPause: Boolean = true

    interface PositionListener {
        fun progress(position: Long)
    }

    override fun run() {
        val position = player.currentPosition
        positionListener.progress(position)
        if (!isPause) {
            handler.postDelayed(this, 150)
        }
    }

    fun purgeHandler() {
        handler.removeCallbacks(this)
    }

    fun pause() {
        isPause = true
    }

    fun resume() {
        isPause = false
        handler.post(this)
    }
}