package com.metaai.studio

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AlphaAnimation
import android.view.animation.AnimationSet
import android.view.animation.ScaleAnimation
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val logo = findViewById<ImageView>(R.id.splashLogo)
        val title = findViewById<TextView>(R.id.splashTitle)
        val subtitle = findViewById<TextView>(R.id.splashSubtitle)

        // Animate logo: scale + fade in
        val logoAnim = AnimationSet(true).apply {
            addAnimation(ScaleAnimation(0.5f, 1f, 0.5f, 1f,
                ScaleAnimation.RELATIVE_TO_SELF, 0.5f,
                ScaleAnimation.RELATIVE_TO_SELF, 0.5f))
            addAnimation(AlphaAnimation(0f, 1f))
            duration = 700
            fillAfter = true
        }
        logo.startAnimation(logoAnim)

        // Animate text
        val textAnim = AlphaAnimation(0f, 1f).apply {
            duration = 800
            startOffset = 400
            fillAfter = true
        }
        title.startAnimation(textAnim)
        subtitle.startAnimation(textAnim)

        // Navigate to MainActivity after 2.2 seconds
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }, 2200)
    }
}
