package com.geras.punk_rockplayer.vm

interface Controller {

    fun next()
    fun previous()
    fun play()
    fun pause()
    fun stop()
    fun seekTo(position: Long)
}