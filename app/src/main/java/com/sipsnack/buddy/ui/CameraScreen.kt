package com.sipsnack.buddy.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.sipsnack.buddy.data.defaultContainers
import com.sipsnack.buddy.rules.Guess
import com.sipsnack.buddy.rules.LogKind
import com.sipsnack.buddy.rules.Recognition
import com.sipsnack.buddy.ui.theme.Sip
import com.sipsnack.buddy.ui.theme.SipType

private sealed interface Step {
    data object Viewfinder : Step
    data object Thinking : Step
    data class Confirm(val guess: Guess?) : Step
}

/** Camera opens only here, only when she taps. One still photo, labelled on the phone, never saved. */
@Composable
fun CameraScreen(vm: AppViewModel, onDone: () -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var step by remember { mutableStateOf<Step>(Step.Viewfinder) }

    Box(Modifier.fillMaxSize().background(Sip.Ink)) {
        when (val s = step) {
            is Step.Confirm -> ConfirmPanel(s.guess, onLog = { kind, cups, label -> vm.log(kind, cups, label); onDone() }, onClose = onDone)
            else -> if (granted) {
                Viewfinder(thinking = s == Step.Thinking, onClose = onDone, onShot = { step = Step.Thinking }, onResult = { step = Step.Confirm(it) })
            } else {
                Column(
                    Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                ) {
                    Text("The camera helps log a sip in one tap.", style = SipType.Title)
                    Text("It only opens when you tap, takes one photo, and the photo never leaves your phone or gets saved.", style = SipType.Body.copy(color = Sip.Muted))
                    PrimaryButton("Allow camera", onClick = { ask.launch(Manifest.permission.CAMERA) }, modifier = Modifier.fillMaxWidth())
                    OutlineButton("Log without a photo", onClick = { step = Step.Confirm(null) }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
private fun Viewfinder(thinking: Boolean, onClose: () -> Unit, onShot: () -> Unit, onResult: (Guess?) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val controller = remember {
        LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            setEnabledUseCases(LifecycleCameraController.IMAGE_CAPTURE)
            bindToLifecycle(lifecycle)
        }
    }
    val labeler = remember { ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS) }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { PreviewView(it).apply { this.controller = controller; scaleType = PreviewView.ScaleType.FILL_CENTER } },
            modifier = Modifier.fillMaxSize(),
        )
        IconButton(
            onClick = onClose,
            modifier = Modifier.statusBarsPadding().padding(16.dp).size(48.dp).background(Sip.Ink.copy(alpha = 0.7f), CircleShape),
        ) { Icon(Icons.Filled.Close, contentDescription = "Close", tint = Sip.Cloud) }

        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(if (thinking) "Looking..." else "Snap your glass, bottle or plate", style = SipType.Label.copy(color = Sip.Cloud),
                modifier = Modifier.background(Sip.Ink.copy(alpha = 0.75f), RoundedCornerShape(999.dp)).padding(horizontal = 14.dp, vertical = 8.dp))
            Box(
                Modifier
                    .size(80.dp)
                    .border(4.dp, Sip.Cloud, CircleShape)
                    .padding(8.dp)
                    .background(if (thinking) Sip.Deep else Sip.Electric, CircleShape)
                    .semantics { contentDescription = "Take photo" }
                    .clickable(enabled = !thinking, role = Role.Button) {
                        onShot()
                        controller.takePicture(ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                val media = image.image
                                if (media == null) { image.close(); onResult(null); return }
                                labeler.process(InputImage.fromMediaImage(media, image.imageInfo.rotationDegrees))
                                    .addOnSuccessListener { labels -> onResult(Recognition.guess(labels.map { it.text to it.confidence })) }
                                    .addOnFailureListener { onResult(null) }
                                    .addOnCompleteListener { image.close() }
                            }

                            override fun onError(exception: ImageCaptureException) = onResult(null)
                        })
                    },
            )
        }
    }
}

private val kindNames = mapOf(
    LogKind.WATER to "Water",
    LogKind.OTHER_DRINK to "Other drink",
    LogKind.MEAL to "Meal",
    LogKind.SNACK to "Snack",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConfirmPanel(guess: Guess?, onLog: (LogKind, Double, String) -> Unit, onClose: () -> Unit) {
    var kind by remember { mutableStateOf(guess?.kind) }
    var container by remember { mutableStateOf(defaultContainers.first()) }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    guess == null -> "What was it?"
                    kind == LogKind.WATER -> "Nice one. How much?"
                    else -> "Got it. Log it?"
                },
                style = SipType.Title,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) { Icon(Icons.Filled.Close, contentDescription = "Close", tint = Sip.Cloud) }
        }

        if (guess != null) {
            Text("Looks like ${kindNames.getValue(guess.kind).lowercase()} · ${(guess.confidence * 100).toInt()}% sure", style = SipType.Small)
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            kindNames.forEach { (k, name) ->
                Choice(name, selected = kind == k, onClick = { kind = k })
            }
        }

        if (kind == LogKind.WATER) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Your usual is ready. Tap another size if it's different.", style = SipType.Small)
                defaultContainers.forEach { c ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .background(if (c == container) Sip.Deep else Sip.Night, RoundedCornerShape(16.dp))
                            .border(if (c == container) 2.dp else 1.dp, if (c == container) Sip.Electric else Sip.Line, RoundedCornerShape(16.dp))
                            .clickable(role = Role.RadioButton) { container = c }
                            .padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(c.name, style = SipType.BodyStrong.copy(color = if (c == container) Sip.Cloud else Sip.Muted), modifier = Modifier.weight(1f))
                        Text(cupsLabel(c.cups), style = SipType.Small.copy(color = if (c == container) Sip.Cloud else Sip.Muted))
                    }
                }
            }
        }

        val chosen = kind
        PrimaryButton(
            text = when (chosen) {
                null -> "Pick what it was"
                LogKind.WATER -> "Log ${cupsLabel(container.cups)}"
                else -> "Log ${kindNames.getValue(chosen).lowercase()}"
            },
            enabled = chosen != null,
            onClick = {
                when (chosen) {
                    LogKind.WATER -> onLog(LogKind.WATER, container.cups, container.name)
                    null -> Unit
                    else -> onLog(chosen, 0.0, kindNames.getValue(chosen))
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Choice(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .background(if (selected) Sip.Ice else Sip.Night, RoundedCornerShape(999.dp))
            .border(1.dp, if (selected) Sip.Ice else Sip.Line, RoundedCornerShape(999.dp))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = SipType.Label.copy(color = if (selected) Sip.Ink else Sip.Cloud)) }
}

private fun cupsLabel(cups: Double): String {
    val n = if (cups % 1.0 == 0.0) cups.toInt().toString() else cups.toString()
    return "$n ${if (cups == 1.0) "cup" else "cups"}"
}
