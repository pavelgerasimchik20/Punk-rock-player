package com.geras.punk_rockplayer.service

import android.content.ComponentName
import android.content.Context
import android.content.Context.BIND_AUTO_CREATE
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.geras.punk_rockplayer.data_source.Song
import com.geras.punk_rockplayer.vm.Command
import javax.inject.Inject

class ServiceConnectionController @Inject constructor(private val context: Context) {

    private val _eventLiveData = MutableLiveData(PlaybackStateCompat.STATE_NONE)
    val eventLiveData: LiveData<Int?> = _eventLiveData

    private val _trackPlaybackPositionLiveData: MutableLiveData<Long?> = MutableLiveData(null)
    val playbackPositionLiveData: LiveData<Long?> = _trackPlaybackPositionLiveData

    private val _currentTrackLiveData: MutableLiveData<Song?> = MutableLiveData(null)
    val currentTrackLiveData: LiveData<Song?> = _currentTrackLiveData

    private var mediaController: MediaControllerCompat? = null

    private var playerServiceBinder: PlayerService.PlayerServiceBinder? = null
    private val callback by lazy { createCallbackService() }
    private val serviceConnection by lazy { createServiceConnection() }

    init {
        context.bindService(
            Intent(context, PlayerService()::class.java),
            serviceConnection,
            BIND_AUTO_CREATE
        )
    }

    private fun createServiceConnection(): ServiceConnection {
        return object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder) {
                playerServiceBinder = service as PlayerService.PlayerServiceBinder

                playerServiceBinder?.let { playerServiceBinder ->
                    playerServiceBinder.setCallbackPlaybackPosition { position ->
                        onChangePosition(position)
                    }
                    playerServiceBinder.setCallbackCurrentTrackChanged { track ->
                        onChangeCurrentTrack(track)
                    }

                    mediaController = try {
                        MediaControllerCompat(
                            context,
                            playerServiceBinder.mediaSessionToken
                        ).apply {
                            registerCallback(callback)
                            callback.onPlaybackStateChanged(playbackState)
                        }
                    } catch (e: RemoteException) {
                        null
                    }
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                playerServiceBinder = null
                mediaController?.unregisterCallback(callback)
                mediaController = null
                context.unbindService(serviceConnection)
            }
        }
    }

    private fun createCallbackService(): MediaControllerCompat.Callback {
        return object : MediaControllerCompat.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
                super.onPlaybackStateChanged(state)
                state ?: return
                if (_eventLiveData.value != state.state) {
                    _eventLiveData.value = state.state
                }
            }
        }
    }

    private fun onChangePosition(position: Long) {
        _trackPlaybackPositionLiveData.value = position
    }

    private fun onChangeCurrentTrack(track: Song) {
        _currentTrackLiveData.value = track
    }

    fun executeCommandPlayer(command: Command) {
        mediaController.apply {
            when (command) {
                Command.NEXT -> mediaController?.transportControls?.skipToNext()
                Command.PREVIOUS -> mediaController?.transportControls?.skipToPrevious()
                Command.PLAY -> {
                    if (_eventLiveData.value != PlaybackStateCompat.STATE_PLAYING) {
                        mediaController?.transportControls?.play()
                    }
                }
                Command.PAUSE -> mediaController?.transportControls?.pause()
                Command.STOP -> mediaController?.transportControls?.stop()
                Command.NONE -> Unit
            }
        }
    }

    fun seekTo(position: Long) {
        mediaController?.transportControls?.seekTo(position)
    }
}