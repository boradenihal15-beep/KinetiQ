

package com.kinetiQ.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kinetiQ.app.core.SessionSummary
import com.kinetiQ.app.ui.components.ExerciseCard
import com.kinetiQ.app.ui.components.PrimaryButton
import com.kinetiQ.app.ui.components.StatCard
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onDone: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(1200)
        onDone()
    }
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("KinetiQ", color = MaterialTheme.colorScheme.primary, fontSize = 44.sp, fontWeight = FontWeight.Bold)
        Text("Real-time form coaching", color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp)
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun HomeScreen(onStartSquat: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text("Hello, athlete", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Let's check your form today.", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
        Spacer(Modifier.height(24.dp))

        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primary) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    "TODAY'S WORKOUT",
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    "Squat - live form check",
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = onStartSquat,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onPrimary,
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) { Text("Start workout", fontWeight = FontWeight.Bold) }
            }
        }

        Spacer(Modifier.height(28.dp))
        Text("Categories", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CategoryChip("Gym", true)
            CategoryChip("Yoga - soon", false)
            CategoryChip("Sports - soon", false)
        }

        Spacer(Modifier.height(28.dp))
        Text("Gym exercises", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ExerciseCard("Squat", "Beginner  -  Legs + Glutes", true, onStartSquat)
            ExerciseCard("Bench Press", "Coming soon", false, {})
            ExerciseCard("Bicep Curl", "Coming soon", false, {})
            ExerciseCard("Shoulder Press", "Coming soon", false, {})
            ExerciseCard("Lunges", "Coming soon", false, {})
        }
    }
}

@Composable
private fun Bullet(text: String) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text("•  ", color = MaterialTheme.colorScheme.primary)
        Text(text, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Spacer(Modifier.height(20.dp))
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
}

@Composable
fun SquatInfoScreen(onStart: () -> Unit, onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text("Squat", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text(
            "Beginner  -  Legs + Glutes",
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )

        SectionTitle("Camera setup")
        Bullet("Place the phone to your side (45 to 90 degrees), about 2 to 3 m away")
        Bullet("Full body in frame, phone at about hip height")
        Bullet("Only one person in view, good lighting")

        SectionTitle("How to squat")
        listOf(
            "Feet about shoulder-width apart",
            "Keep your back in a neutral position",
            "Bend your knees and lower your hips",
            "Keep your knees aligned with your feet",
            "Reach a comfortable depth",
            "Push through your feet to stand up"
        ).forEachIndexed { i, s -> Bullet("${i + 1}. $s") }

        SectionTitle("Common mistakes")
        Bullet("Not going deep enough")
        Bullet("Leaning too far forward")
        Bullet("Knees caving inward")

        SectionTitle("Safety")
        Text(
            "KinetiQ gives fitness-form estimates, not medical advice. " +
                    "Stop if you feel pain. Your camera video is analysed on this device and is not recorded or uploaded.",
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )

        Spacer(Modifier.height(24.dp))
        PrimaryButton(text = "START CAMERA", onClick = onStart)
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
}

@Composable
fun SummaryScreen(s: SessionSummary, onTryAgain: () -> Unit, onFinish: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text("Workout complete", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Squat  -  %d:%02d".format(s.durationSec / 60, s.durationSec % 60),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Total reps", "${s.totalReps}", Modifier.weight(1f))
            StatCard("Good reps", "${s.goodReps}", Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Needs work", "${s.needsImprovement}", Modifier.weight(1f))
            StatCard("Avg form score", if (s.totalReps == 0) "--" else "${s.avgScore}%", Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        StatCard("Most common issue", s.topIssue ?: "None", Modifier.fillMaxWidth())

        SectionTitle("Rep by rep")
        if (s.reps.isEmpty()) {
            Text("No complete reps were detected.")
        } else {
            s.reps.forEach { r ->
                Text(
                    "Rep ${r.number}  -  ${if (r.good) "Good" else "Needs improvement"}  -  score ${r.score}" +
                            if (r.issues.isEmpty()) "" else "  (${r.issues.joinToString { it.label }})",
                    modifier = Modifier.padding(vertical = 3.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Fitness-form estimate only, not a medical diagnosis.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
        Spacer(Modifier.height(20.dp))
        PrimaryButton(text = "TRY AGAIN", onClick = onTryAgain)
        TextButton(onClick = onFinish, modifier = Modifier.fillMaxWidth()) { Text("Finish") }
    }
}