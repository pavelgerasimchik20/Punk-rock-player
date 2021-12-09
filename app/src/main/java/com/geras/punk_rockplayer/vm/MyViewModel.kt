package com.geras.punk_rockplayer.vm

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "TAG from MyViewModel"

class MyViewModel @Inject constructor() : ViewModel(), Controller {

    private val _commandToPlayerFlow = MutableSharedFlow<Command>()
    val commandToPlayerFlow = _commandToPlayerFlow.asSharedFlow()

    private val _setPlaybackPositionFlow = MutableSharedFlow<Long>()
    val setPlaybackPositionFlow = _setPlaybackPositionFlow.asSharedFlow()

    override fun seekTo(position: Long) {
        viewModelScope.launch {
            _setPlaybackPositionFlow.emit(position)
        }
    }

    private fun postCommand(command: Command) {
        viewModelScope.launch {
            _commandToPlayerFlow.emit(command)
        }
    }

    override fun next() {
        Log.d(TAG, "next")
        postCommand(Command.NEXT)
    }

    override fun previous() {
        Log.d(TAG, "previous")
        postCommand(Command.PREVIOUS)
    }

    override fun play() {
        Log.d(TAG, "play")
        postCommand(Command.PLAY)
    }

    override fun pause() {
        Log.d(TAG, "pause")
        postCommand(Command.PAUSE)
    }

    override fun stop() {
        Log.d(TAG, "stop")
        postCommand(Command.STOP)
    }
}