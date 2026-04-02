package com.echoproduction.metroclock.ui

import android.net.Uri
import android.view.ViewGroup
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import com.echoproduction.metroclock.R

@Composable
fun SplashVideoScreen(onFinished: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                VideoView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    val uri = Uri.parse("android.resource://${context.packageName}/${R.raw.splash}")
                    setVideoURI(uri)
                    setOnCompletionListener { onFinished() }
                    setOnPreparedListener { mp ->
                        mp.isLooping = false
                        start()
                    }
                }
            }
        )
    }
}
