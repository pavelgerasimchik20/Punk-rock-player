package com.geras.punk_rockplayer.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class MyViewModel : ViewModel(), Controller {

    private val _getActionComand = MutableSharedFlow<Command>()
    val getActionComand = _getActionComand.asSharedFlow()

    private val _setPlayPosition = MutableSharedFlow<Long>()
    val setPlayPosition = _setPlayPosition.asSharedFlow()

    override fun search(position: Long) {
        viewModelScope.launch {
            _setPlayPosition.emit(position)
        }
    }

    private fun runCommandByUsingCoroutine(command: Command) {
        viewModelScope.launch {
            _getActionComand.emit(command)
        }
    }

    override fun next() {
        runCommandByUsingCoroutine(Command.NEXT)
    }

    override fun previous() {
        runCommandByUsingCoroutine(Command.PREVIOUS)
    }

    override fun play() {
        runCommandByUsingCoroutine(Command.PLAY)
    }

    override fun pause() {
        runCommandByUsingCoroutine(Command.PAUSE)
    }

    override fun stop() {
        runCommandByUsingCoroutine(Command.STOP)
    }
}

enum class Command {
    PREVIOUS, PLAY, NEXT, PAUSE, STOP
}