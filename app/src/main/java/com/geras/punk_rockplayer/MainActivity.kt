package com.geras.punk_rockplayer

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.support.v4.media.session.PlaybackStateCompat
import android.text.method.ScrollingMovementMethod
import android.widget.SeekBar
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.geras.punk_rockplayer.converters.timeToString
import com.geras.punk_rockplayer.data_source.Song
import com.geras.punk_rockplayer.databinding.ActivityMainBinding
import com.geras.punk_rockplayer.service.ServiceConnectionController
import com.geras.punk_rockplayer.vm.Command
import com.geras.punk_rockplayer.vm.Controller
import com.geras.punk_rockplayer.vm.MyViewModel
import kotlinx.coroutines.flow.collect
import javax.inject.Inject

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private var isSeekBarTrackingTouch = false
    private var currentTrack: Song? = null

    val model: MyViewModel by viewModels()
    private val playerControl: Controller
        get() = model

    @Inject
    lateinit var connectionController: ServiceConnectionController

    override fun onCreate(savedInstanceState: Bundle?) {
        (application as PlayerApplication).appComponent.inject(this)

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // enable auto-scrolling for textLog
        binding.textLog.movementMethod = ScrollingMovementMethod()

        setListenersPlayerControl()
        setListenerSeekBar()
        registerObserversStatePlayer()
        lunchCollectPlaybackPosition()
        lunchCollectCommandToPlayer()
        prefatorySetSeekBarSettings()
        showCurrentTrackInfo()
    }

    private fun lunchCollectPlaybackPosition() {
        lifecycleScope.launchWhenStarted {
            model.setPlaybackPositionFlow.collect {
                connectionController.seekTo(it)
            }
        }
    }

    private fun lunchCollectCommandToPlayer() {
        lifecycleScope.launchWhenStarted {
            model.commandToPlayerFlow.collect {
                applyCommandPlayer(it)
            }
        }
    }

    private fun registerObserversStatePlayer() {
        connectionController.eventLiveData.observe(this, {
            it?.let {
                playerStateApply(it)
            }
        })

        connectionController.currentTrackLiveData.observe(this, {
            it?.let {
                currentTrack = it
                toLog("Track: ${it.title}")
                showCurrentTrackInfo()
            }
        })

        connectionController.playbackPositionLiveData.observe(this, {
            it?.let {
                if (!isSeekBarTrackingTouch) {
                    showProgressBar(it)
                }
            }
        })
    }

    private fun applyCommandPlayer(command: Command) {
        connectionController.executeCommandPlayer(command)
    }

    private fun playerStateApply(@PlaybackStateCompat.State state: Int) {
        when (state) {
            PlaybackStateCompat.STATE_BUFFERING -> {
                toLog("Player state: STATE_BUFFERING")
            }
            PlaybackStateCompat.STATE_CONNECTING -> {
                toLog("Player state: STATE_CONNECTING")
            }
            PlaybackStateCompat.STATE_ERROR -> {
                toLog("Player state: STATE_ERROR")
            }
            PlaybackStateCompat.STATE_FAST_FORWARDING -> {
                toLog("Player state: STATE_FAST_FORWARDING")
            }
            PlaybackStateCompat.STATE_NONE -> {
                toLog("Player state: STATE_NONE")
            }
            PlaybackStateCompat.STATE_PAUSED -> {
                toLog("Player state: STATE_PAUSED")
                showButtonPlay()
                showButtonStop(true)
            }
            PlaybackStateCompat.STATE_PLAYING -> {
                toLog("Player state: STATE_PLAYING")
                showButtonPause()
                showButtonStop(true)
            }
            PlaybackStateCompat.STATE_REWINDING -> {
                toLog("Player state: STATE_REWINDING")
            }
            PlaybackStateCompat.STATE_SKIPPING_TO_NEXT -> {
                toLog("Player state: STATE_SKIPPING_TO_NEXT")
                showCurrentTrackInfo()
            }
            PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS -> {
                toLog("Player state: STATE_SKIPPING_TO_PREVIOUS")
                showCurrentTrackInfo()
            }
            PlaybackStateCompat.STATE_SKIPPING_TO_QUEUE_ITEM -> {
                toLog("Player state: STATE_SKIPPING_TO_QUEUE_ITEM")
            }
            PlaybackStateCompat.STATE_STOPPED -> {
                toLog("Player state: STATE_STOPPED")
                showButtonPlay()
                showButtonStop(false)
            }
        }
    }

    private fun setListenersPlayerControl() {
        binding.playerNext.setOnClickListener {
            toLog("Click Next button")
            playerControl.next()
        }
        binding.playerPrevious.setOnClickListener {
            toLog("Click Previous button")
            playerControl.previous()
        }
        binding.playerPlay.setOnClickListener {
            toLog("Click Play button")
            playerControl.play()
        }
        binding.playerPause.setOnClickListener {
            toLog("Click Pause button")
            playerControl.pause()
        }
        binding.playerStop.setOnClickListener {
            toLog("Click Stop button")
            playerControl.stop()
        }
    }

    private fun setListenerSeekBar() {
        binding.playSeekBar.setOnSeekBarChangeListener(object :
            SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(
                playSeekBar: SeekBar?,
                progress: Int,
                fromUser: Boolean
            ) {
                setSeekBarPosition(progress.toLong())
            }

            override fun onStartTrackingTouch(playSeekBar: SeekBar?) {
                isSeekBarTrackingTouch = true
            }

            override fun onStopTrackingTouch(playSeekBar: SeekBar?) {
                playSeekBar?.let {
                    playerControl.seekTo(it.progress.toLong())
                    isSeekBarTrackingTouch = false
                }
            }
        })
    }

    private fun showButtonPause() {
        binding.playerPlay.isVisible = false
        binding.playerPause.isVisible = true
    }

    private fun showButtonPlay() {
        binding.playerPause.isVisible = false
        binding.playerPlay.isVisible = true
    }

    private fun showButtonStop(value: Boolean) {
        binding.playerStop.isVisible = value
    }

    private fun prefatorySetSeekBarSettings() {
        setSeekBarPosition(0)
        currentTrack?.let {
            setSeekBarDuration(it.duration)
        } ?: setSeekBarDuration(0)
    }

    private fun setSeekBarPosition(position: Long) {
        binding.playPosition.text = timeToString(position)
    }

    private fun setSeekBarDuration(duration: Long) {
        var applyDuration = 0L
        if (duration > 0) {
            applyDuration = duration
        }
        binding.playSeekBar.max = applyDuration.toInt()
        binding.playDuration.text = timeToString(applyDuration)
    }

    private fun showCurrentTrackInfo() {
        currentTrack?.let { track ->
            showCover(track.bitmapUri)
            binding.trackTitle.text = track.title
            binding.trackArtist.text = track.artist
            setSeekBarDuration(track.duration)
        }
    }

    private fun showProgressBar(progress: Long) {
        val progressView = progress.toInt()
        if (progressView != binding.playSeekBar.progress) {
            binding.playSeekBar.progress = progressView
        }
    }

    private fun toLog(event: String) {
        binding.textLog.append("$event\n")
    }

    private fun showCover(uri: String) {
        val view = binding.trackCover

        Glide.with(view.context)
            .load(uri)
            .placeholder(R.drawable.ic_baseline_cloud_download_24)
            .error(R.drawable.ic_baseline_error_outline_24)
            .centerCrop()
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable>?,
                    isFirstResource: Boolean
                ): Boolean {
                    toLog("Image loading - Error")
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable?,
                    model: Any?,
                    target: Target<Drawable>?,
                    dataSource: DataSource?,
                    isFirstResource: Boolean
                ): Boolean {
                    toLog("Image loading - Ok")
                    return false
                }
            })
            .into(view)
    }
}
