package com.sipsnack.buddy

import android.app.Application
import com.sipsnack.buddy.reminders.Nudger

class SipApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Nudger.createChannels(this)
        Nudger.schedule(this)
    }
}
