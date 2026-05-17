package com.dietician.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.dietician.shared.Dietician

fun main() =
    application {
        Window(onCloseRequest = ::exitApplication, title = "Dietician ${Dietician.VERSION}") {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    DieticianDesktopHome()
                }
            }
        }
    }

@Composable
private fun DieticianDesktopHome() {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Dietician ${Dietician.VERSION}", style = MaterialTheme.typography.headlineLarge)
        Text("Spec date: ${Dietician.SPEC_DATE}")
        Text(
            "Desktop scaffold placeholder. See docs/superpowers/specs/2026-05-17-dietician-design.md\n" +
                "Subprocesses (ClaudeMax CLI, Playwright, whisper.cpp, yt-dlp) wired in implementation phase.",
        )
    }
}
