package com.sipsnack.buddy

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sipsnack.buddy.reminders.Nudger
import com.sipsnack.buddy.rules.Moment
import com.sipsnack.buddy.ui.AppViewModel
import com.sipsnack.buddy.ui.BuddyScreen
import com.sipsnack.buddy.ui.RoleScreen
import com.sipsnack.buddy.ui.ShareAskCard
import com.sipsnack.buddy.ui.SharingScreen
import com.sipsnack.buddy.ui.CameraScreen
import com.sipsnack.buddy.ui.MemeCard
import com.sipsnack.buddy.ui.SettingsScreen
import com.sipsnack.buddy.ui.TodayScreen
import com.sipsnack.buddy.ui.theme.Sip
import com.sipsnack.buddy.ui.theme.SipTheme
import com.sipsnack.buddy.ui.theme.SipType
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        if (savedInstanceState == null) handleNudge(intent)

        setContent {
            SipTheme {
                var screen by rememberSaveable { mutableStateOf("today") }
                val moment by vm.moment.collectAsState()
                val toast by vm.toast.collectAsState()
                val role by vm.role.collectAsState()
                val askShare by vm.askShare.collectAsState()

                BackHandler(enabled = screen != "today") { screen = "today" }

                Box(Modifier.fillMaxSize().background(Sip.Ink)) {
                    when {
                        role == null -> RoleScreen(onChoose = { vm.chooseRole(it); screen = "today" })
                        role == "buddy" -> BuddyScreen(vm)
                        screen == "camera" -> CameraScreen(vm, onDone = { screen = "today" })
                        screen == "settings" -> SettingsScreen(vm, onBack = { screen = "today" }, onSharing = { screen = "sharing" })
                        screen == "sharing" -> SharingScreen(vm, onBack = { screen = "today" })
                        else -> TodayScreen(vm, onSnap = { screen = "camera" }, onSettings = { screen = "settings" }, onSharing = { screen = "sharing" })
                    }

                    if (role == "her" && askShare && moment == null) {
                        ShareAskCard(onYes = vm::acceptShare, onNotNow = vm::declineShare)
                    }

                    toast?.let { text ->
                        LaunchedEffect(text) { delay(2200); vm.dismissToast() }
                        Text(
                            text,
                            style = SipType.Label.copy(color = Sip.Ink),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .statusBarsPadding()
                                .padding(top = 12.dp)
                                .background(Sip.Electric, RoundedCornerShape(999.dp))
                                .padding(horizontal = 18.dp, vertical = 10.dp),
                        )
                    }

                    moment?.let { m ->
                        MemeCard(
                            moment = m,
                            onAction = {
                                vm.dismissMoment()
                                if (m == Moment.DELAY || m == Moment.STILL_DELAYING) screen = "camera"
                            },
                            onDismiss = vm::dismissMoment,
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNudge(intent)
    }

    /** A tapped nudge notification opens the app straight into its meme video. */
    private fun handleNudge(intent: Intent?) {
        val name = intent?.getStringExtra(Nudger.EXTRA_MOMENT) ?: return
        runCatching { Moment.valueOf(name) }.getOrNull()?.let(vm::show)
        intent.removeExtra(Nudger.EXTRA_MOMENT)
    }
}
