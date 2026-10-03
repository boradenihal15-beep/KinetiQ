package com.kinetiQ.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kinetiQ.app.ui.theme.KinetiQTheme

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        Text(text)
    }
}

@Preview(showBackground = true)
@Composable
private fun PrimaryButtonPreview() {
    KinetiQTheme {
        PrimaryButton(text = "START WORKOUT", onClick = {})
    }
}