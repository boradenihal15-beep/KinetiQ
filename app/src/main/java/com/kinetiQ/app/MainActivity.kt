package com.kinetiQ.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.kinetiQ.app.core.SessionSummary
import com.kinetiQ.app.ui.screens.HomeScreen
import com.kinetiQ.app.ui.screens.LiveScreen
import com.kinetiQ.app.ui.screens.SplashScreen
import com.kinetiQ.app.ui.screens.SquatInfoScreen
import com.kinetiQ.app.ui.screens.SummaryScreen
import com.kinetiQ.app.ui.theme.KinetiQTheme

private sealed interface Screen {
    data object Splash : Screen
    data object Home : Screen
    data object Info : Screen
    data object Live : Screen
    data class Summary(val data: SessionSummary) : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            KinetiQTheme(darkTheme = true) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    App()
                }
            }
        }
    }
}

@Composable
private fun App() {
    var screen by remember { mutableStateOf<Screen>(Screen.Splash) }

    BackHandler(enabled = screen is Screen.Info || screen is Screen.Live) {
        screen = if (screen is Screen.Live) Screen.Info else Screen.Home
    }

    when (val s = screen) {
        Screen.Splash -> SplashScreen(onDone = { screen = Screen.Home })
        Screen.Home -> HomeScreen(onStartSquat = { screen = Screen.Info })
        Screen.Info -> SquatInfoScreen(onStart = { screen = Screen.Live }, onBack = { screen = Screen.Home })
        Screen.Live -> LiveScreen(
            onFinish = { screen = Screen.Summary(it) },
            onBack = { screen = Screen.Info }
        )
        is Screen.Summary -> SummaryScreen(
            s = s.data,
            onTryAgain = { screen = Screen.Live },
            onFinish = { screen = Screen.Home }
        )
    }
}