package com.climtech.adlcollector.core.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.climtech.adlcollector.core.ui.theme.ADLCollectorTheme

@Composable
fun ErrorScreen(
    error: String,
    onRetry: () -> Unit
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Circular icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }

            Spacer(Modifier.height(24.dp))

            // Main title
            Text(
                "An Error Occurred",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(12.dp))

            // Error message
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(32.dp))

            Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

@Preview(showBackground = true, name = "Error • Light")
@Composable
private fun PreviewErrorLight() {
    ADLCollectorTheme {
        ErrorScreen(
            error = "Failed to load ADL Instances: Network timeout.",
            onRetry = {}
        )
    }
}

@Preview(
    showBackground = true,
    name = "Error • Dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun PreviewErrorDark() {
    ADLCollectorTheme(darkTheme = true) {
        ErrorScreen(
            error = "Token exchange failed: invalid_grant",
            onRetry = {}
        )
    }
}