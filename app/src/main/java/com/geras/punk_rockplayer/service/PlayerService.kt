package com.geras.punk_rockplayer.service

import android.annotation.SuppressLint
import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioManager.OnAudioFocusChangeListener
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.media.session.MediaButtonReceiver
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.geras.punk_rockplayer.MainActivity
import com.geras.punk_rockplayer.PlayerApplication
import com.geras.punk_rockplayer.R
import com.geras.punk_rockplayer.data_source.Song
import com.geras.punk_rockplayer.data_source.SongsRepo
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.DefaultRenderersFactory.ExtensionRendererMode
import com.google.android.exoplayer2.source.TrackGroupArray
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.TrackSelectionArray
import javax.inject.Inject

class PlayerService : Service() {
    @Inject
    lateinit var tracks: SongsRepo

    private var progressTracker: ProgressTracker? = null
    private var callbackPlaybackPositionChanged: ((position: Long) -> Unit)? = null
    private var callbackCurrentTrackChanged: ((track: Song) -> Unit)? = null
    private var oldCurrentTrack: Song? = null

    private val metadataBuilder = MediaMetadataCompat.Builder()
    private val stateBuilder = createStateBuilder()
    private val audioManager by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }
    private val mediaSession: MediaSessionCompat by lazy { createMediaSession() }
    private val exoPlayer by lazy { createExoPlayer() }

    private var audioFocusRequest: AudioFocusRequest? = null
    private var audioFocusRequested = false

    private val mediaSessionCallback: MediaSessionCompat.Callback =
        object : MediaSessionCompat.Callback() {
            private var currentUri: String? = null
            var currentState = PlaybackStateCompat.STATE_STOPPED

            override fun onPlay() {
                if (!exoPlayer.isPlaying) {
                    startService(Intent(applicationContext, PlayerService::class.java))
                    val track = tracks.current

                    updateMetadataFromTrack(track)
                    prepareToPlay(track.trackUri)

                    if (!audioFocusRequested) {
                        audioFocusRequested = if (requestAudioFocus()) true else return
                    }
                    mediaSession.isActive = true

                    registerReceiver(
                        becomingNoisyReceiver,
                        IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
                    )
                    exoPlayer.play()
                    progressTracker?.resume()
                }

                mediaSession.setPlaybackState(
                    stateBuilder.setState(
                        PlaybackStateCompat.STATE_PLAYING,
                        exoPlayer.currentPosition,
                        1f
                    ).build()
                )
                currentState = PlaybackStateCompat.STATE_PLAYING
                refreshNotificationAndForegroundStatus(currentState)
                onChangeCurrentTrack()
            }

            override fun onSeekTo(pos: Long) {
                super.onSeekTo(pos)
                exoPlayer.seekTo(pos)
                mediaSession.setPlaybackState(
                    stateBuilder.setState(
                        currentState,
                        pos,
                        1f
                    ).build()
                )
                refreshNotificationAndForegroundStatus(currentState)
            }

            override fun onPause() {
                if (exoPlayer.isPlaying) {
                    exoPlayer.pause()
                    unregisterReceiver(becomingNoisyReceiver)
                }
                progressTracker?.pause()
                mediaSession.setPlaybackState(
                    stateBuilder.setState(
                        PlaybackStateCompat.STATE_PAUSED,
                        exoPlayer.currentPosition,
                        0f
                    ).build()
                )
                currentState = PlaybackStateCompat.STATE_PAUSED
                refreshNotificationAndForegroundStatus(currentState)
            }

            override fun onStop() {
                progressTracker?.purgeHandler()
                if (exoPlayer.isPlaying) {
                    exoPlayer.pause()
                    unregisterReceiver(becomingNoisyReceiver)
                }

                if (audioFocusRequested) {
                    abandonAudioFocus()
                    audioFocusRequested = false
                }
                mediaSession.isActive = false
                mediaSession.setPlaybackState(
                    stateBuilder.setState(
                        PlaybackStateCompat.STATE_STOPPED,
                        PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                        0f
                    ).build()
                )
                currentState = PlaybackStateCompat.STATE_STOPPED
                refreshNotificationAndForegroundStatus(currentState)
                stopSelf()
            }

            override fun onSkipToNext() {
                val track = tracks.next

                updateMetadataFromTrack(track)
                mediaSession.setPlaybackState(
                    stateBuilder.setState(
                        PlaybackStateCompat.STATE_SKIPPING_TO_NEXT,
                        PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                        1f
                    ).build()
                )
                mediaSession.setPlaybackState(
                    stateBuilder.setState(
                        currentState,
                        PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                        1f
                    ).build()
                )
                refreshNotificationAndForegroundStatus(currentState)
                prepareToPlay(track.trackUri)
                onChangeCurrentTrack()
            }

            override fun onSkipToPrevious() {
                val track = tracks.previous

                updateMetadataFromTrack(track)
                mediaSession.setPlaybackState(
                    stateBuilder.setState(
                        PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS,
                        PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                        1f
                    ).build()
                )
                mediaSession.setPlaybackState(
                    stateBuilder.setState(
                        currentState,
                        PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                        1f
                    ).build()
                )
                refreshNotificationAndForegroundStatus(currentState)
                prepareToPlay(track.trackUri)
                onChangeCurrentTrack()
            }

            private fun requestAudioFocus(): Boolean {
                var audioFocusResult: Int = AudioManager.AUDIOFOCUS_REQUEST_FAILED
                @Suppress("DEPRECATION")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    audioFocusRequest?.let { audioFocusRequest ->
                        audioFocusResult = audioManager.requestAudioFocus(audioFocusRequest)
                    }
                } else {
                    audioFocusResult = audioManager.requestAudioFocus(
                        audioFocusChangeListener,
                        AudioManager.STREAM_MUSIC,
                        AudioManager.AUDIOFOCUS_GAIN
                    )
                }
                return (audioFocusResult == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            }

            private fun abandonAudioFocus() {
                @Suppress("DEPRECATION")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    audioFocusRequest?.let { audioFocusRequest ->
                        audioManager.abandonAudioFocusRequest(audioFocusRequest)
                    }
                } else {
                    audioManager.abandonAudioFocus(audioFocusChangeListener)
                }
            }

            private fun prepareToPlay(uri: String) {
                if (uri != currentUri) {
                    currentUri = uri
                    val mediaItem = MediaItem.fromUri(uri)

                    exoPlayer.run {
                        setMediaItem(mediaItem)
                        prepare()
                    }
                }
            }

            private fun updateMetadataFromTrack(track: Song) {
                asyncLoadBitmap(track.bitmapUri)
                metadataBuilder.apply {
                    putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.title)
                    putString(MediaMetadataCompat.METADATA_KEY_ALBUM, track.artist)
                    putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.artist)
                    putLong(MediaMetadataCompat.METADATA_KEY_DURATION, track.duration.toLong())
                    mediaSession.setMetadata(this.build())
                }
            }

            private fun asyncLoadBitmap(uri: String) {
                Glide.with(applicationContext)
                    .asBitmap()
                    .placeholder(R.drawable.ic_baseline_cloud_download_24)
                    .error(R.drawable.ic_baseline_error_outline_24)
                    .load(uri)
                    .into(object : CustomTarget<Bitmap>() {
                        override fun onResourceReady(
                            resource: Bitmap,
                            transition: Transition<in Bitmap>?
                        ) {
                            if (tracks.current.bitmapUri == uri) {
                                metadataBuilder.apply {
                                    putBitmap(MediaMetadataCompat.METADATA_KEY_ART, resource)
                                    mediaSession.setMetadata(this.build())
                                    refreshNotificationAndForegroundStatus(currentState)
                                }
                            }
                        }

                        override fun onLoadCleared(placeholder: Drawable?) = Unit
                    })
            }
        }

    private val audioFocusChangeListener: OnAudioFocusChangeListener =
        OnAudioFocusChangeListener { focusChange ->
            when (focusChange) {
                AudioManager.AUDIOFOCUS_GAIN -> mediaSessionCallback.onPlay()
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> mediaSessionCallback.onPause()
                else -> mediaSessionCallback.onPause()
            }
        }

    private val becomingNoisyReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Disconnecting headphones - stop playback
            if ((AudioManager.ACTION_AUDIO_BECOMING_NOISY == intent.action)) {
                mediaSessionCallback.onPause()
            }
        }
    }

    private val exoPlayerListener: Player.Listener = object : Player.Listener {
        override fun onTracksChanged(
            trackGroups: TrackGroupArray,
            trackSelections: TrackSelectionArray
        ) = Unit

        override fun onLoadingChanged(isLoading: Boolean) = Unit

        override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
            if (playWhenReady && playbackState == ExoPlayer.STATE_ENDED) {
                mediaSessionCallback.onSkipToNext()
            }
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) = Unit
    }

    override fun onCreate() {
        (application as PlayerApplication).appComponent.inject(this)

        super.onCreate()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(
                NOTIFICATION_DEFAULT_CHANNEL_ID,
                getString(R.string.notification_name),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(notificationChannel)
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .setAcceptsDelayedFocusGain(false)
                .setWillPauseWhenDucked(true)
                .setAudioAttributes(audioAttributes)
                .build()
        }

        val mediaButtonIntent = Intent(
            Intent.ACTION_MEDIA_BUTTON, null, applicationContext,
            MediaButtonReceiver::class.java
        )
        mediaSession.setMediaButtonReceiver(
            PendingIntent.getBroadcast(
                applicationContext,
                0,
                mediaButtonIntent,
                0
            )
        )
    }

    private fun createStateBuilder(): PlaybackStateCompat.Builder {
        return PlaybackStateCompat.Builder().setActions(
            PlaybackStateCompat.ACTION_PLAY
                    or PlaybackStateCompat.ACTION_STOP
                    or PlaybackStateCompat.ACTION_PAUSE
                    or PlaybackStateCompat.ACTION_PLAY_PAUSE
                    or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                    or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                    or PlaybackStateCompat.ACTION_SEEK_TO
        )
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    private fun createMediaSession(): MediaSessionCompat {
        return MediaSessionCompat(this, "PlayerService").apply {
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                setFlags(
                    MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                            MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
                )
            }

            val activityIntent = Intent(applicationContext, MainActivity::class.java)
            setCallback(mediaSessionCallback)
            setSessionActivity(
                PendingIntent.getActivity(
                    applicationContext,
                    0,
                    activityIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
        }
    }

    private fun createExoPlayer(): SimpleExoPlayer {
        @ExtensionRendererMode
        val extensionRendererMode = DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
        val renderersFactory =
            DefaultRenderersFactory(this).setExtensionRendererMode(extensionRendererMode)
        return SimpleExoPlayer.Builder(this, renderersFactory)
            .setTrackSelector(DefaultTrackSelector(applicationContext))
            .setLoadControl(DefaultLoadControl.Builder().build())
            .build().apply {
                addListener(exoPlayerListener)
                setProgressTracker(this)
            }
    }

    private fun setProgressTracker(player: SimpleExoPlayer) {
        progressTracker = ProgressTracker(player, object : ProgressTracker.PositionListener {
            override fun progress(position: Long) {
                callbackPlaybackPositionChanged?.invoke(position)
            }
        })
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        MediaButtonReceiver.handleIntent(mediaSession, intent)
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaSession.release()
        exoPlayer.release()
    }

    override fun onBind(intent: Intent): IBinder {
        return PlayerServiceBinder()
    }

    inner class PlayerServiceBinder : Binder() {
        val mediaSessionToken: MediaSessionCompat.Token
            get() = mediaSession.sessionToken

        fun setCallbackPlaybackPosition(callback: (position: Long) -> Unit) {
            callbackPlaybackPositionChanged = callback
        }

        fun setCallbackCurrentTrackChanged(callback: (track: Song) -> Unit) {
            callbackCurrentTrackChanged = callback
            onChangeCurrentTrack()
        }
    }

    private fun onChangeCurrentTrack() {
        if (oldCurrentTrack != tracks.current) {
            oldCurrentTrack = tracks.current
            callbackCurrentTrackChanged?.invoke(tracks.current)
        }
    }

    private fun refreshNotificationAndForegroundStatus(playbackState: Int) {
        when (playbackState) {
            PlaybackStateCompat.STATE_PLAYING -> {
                startForeground(NOTIFICATION_ID, getNotification(playbackState))
            }
            PlaybackStateCompat.STATE_PAUSED -> {
                NotificationManagerCompat.from(this@PlayerService)
                    .notify(NOTIFICATION_ID, getNotification(playbackState))
                stopForeground(false)
            }
            else -> {
                stopForeground(true)
            }
        }
    }

    private fun getNotification(playbackState: Int): Notification {
        val builder: NotificationCompat.Builder =
            MediaStyleHelper.from(this, mediaSession, NOTIFICATION_DEFAULT_CHANNEL_ID)
        builder.addAction(
            NotificationCompat.Action(
                R.drawable.ic_baseline_skip_previous_24,
                getString(R.string.previous_button),
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                    this,
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                )
            )
        )
        if (playbackState == PlaybackStateCompat.STATE_PLAYING) builder.addAction(
            NotificationCompat.Action(
                R.drawable.ic_baseline_pause_24,
                getString(R.string.pause_button),
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                    this,
                    PlaybackStateCompat.ACTION_PLAY_PAUSE
                )
            )
        ) else builder.addAction(
            NotificationCompat.Action(
                R.drawable.ic_baseline_play_arrow_24,
                getString(R.string.play_button),
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                    this,
                    PlaybackStateCompat.ACTION_PLAY_PAUSE
                )
            )
        )
        builder.addAction(
            NotificationCompat.Action(
                R.drawable.ic_baseline_skip_next_24,
                getString(R.string.next_button),
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                    this,
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                )
            )
        )

        builder.setStyle(
            androidx.media.app.NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(0, 1, 2)
                .setShowCancelButton(true)
                .setCancelButtonIntent(
                    MediaButtonReceiver.buildMediaButtonPendingIntent(
                        this,
                        PlaybackStateCompat.ACTION_STOP
                    )
                )
                .setMediaSession(mediaSession.sessionToken)
        )
        builder.setSmallIcon(R.drawable.ic_baseline_music_note_24)
        builder.color = ContextCompat.getColor(this, R.color.primaryDarkColor)
        builder.setShowWhen(false)
        builder.priority = NotificationCompat.PRIORITY_HIGH
        return builder.build()
    }

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val NOTIFICATION_DEFAULT_CHANNEL_ID = "default_channel"
    }
}