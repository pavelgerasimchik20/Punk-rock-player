package com.geras.punk_rockplayer

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.geras.punk_rockplayer.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

    }


}