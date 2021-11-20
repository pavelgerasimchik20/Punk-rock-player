package com.geras.punk_rockplayer.vm

interface Controller {

    fun search(position: Long)
    fun previous()
    fun play()
    fun next()
    fun pause()
    fun stop()
}