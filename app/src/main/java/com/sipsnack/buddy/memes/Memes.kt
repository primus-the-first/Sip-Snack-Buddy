package com.sipsnack.buddy.memes

import com.sipsnack.buddy.HER_NAME
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import androidx.annotation.RawRes
import com.sipsnack.buddy.R
import com.sipsnack.buddy.rules.Moment

/** What plays for each moment. Clips live in res/raw; notifications can only play the sound. */
data class Meme(
    val title: String,
    val subtitle: String,
    @RawRes val sound: Int?,
    @RawRes val video: Int?,
    val button: String,
)

object Memes {
    fun forMoment(moment: Moment, name: String = HER_NAME): Meme = when (moment) {
        Moment.CUP_LOGGED -> Meme("Let's have it!", "Cup logged.", R.raw.cup_logged_sound, null, "Nice")
        Moment.HALFWAY -> Meme("Halfway there!", "Let's have it, let's have it.", R.raw.halfway_sound, R.raw.halfway_video, "Keep going")
        Moment.GOAL_HIT -> Meme("Goal hit!", "You did it, $name. He's dancing for you.", R.raw.goal_hit_sound, R.raw.goal_hit_video, "Back to my day")
        Moment.DELAY -> Meme("Hmm. We see you.", "The sea lion has noticed. Time for some water.", R.raw.delay_sound, R.raw.delay_video, "Okay okay, log a sip")
        Moment.STILL_DELAYING -> Meme("Go and sit down...", "...with your water. Akrobeto is not playing.", R.raw.still_delaying_sound, R.raw.still_delaying_video, "Fine, log a sip")
        Moment.MEAL_DELAY -> Meme("Food first, $name.", "You haven't logged a meal yet. Eat something good.", null, null, "On it")
    }

    /** Silent and vibrate modes mean no meme audio; videos still play muted. */
    fun soundAllowed(context: Context): Boolean {
        val audio = context.getSystemService(AudioManager::class.java)
        return audio.ringerMode == AudioManager.RINGER_MODE_NORMAL
    }

    private var player: MediaPlayer? = null

    fun playSound(context: Context, @RawRes res: Int) {
        if (!soundAllowed(context)) return
        player?.release()
        player = MediaPlayer.create(
            context.applicationContext,
            res,
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
            context.getSystemService(AudioManager::class.java).generateAudioSessionId(),
        )?.apply {
            setOnCompletionListener { it.release(); if (player === it) player = null }
            start()
        }
    }
}
