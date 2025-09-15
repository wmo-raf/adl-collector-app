package com.climtech.adlcollector.core.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.climtech.adlcollector.core.ui.theme.ADLCollectorTheme

@Composable
fun LoadingScreen() {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text("Working…", style = MaterialTheme.typography.bodyLarge)
        }
    }
}


@Preview(showBackground = true, name = "Loading • Light")
@Composable
private fun PreviewLoadingLight() {
    ADLCollectorTheme {
        LoadingScreen()
    }
}

@Preview(
    showBackground = true,
    name = "Loading • Dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun PreviewLoadingDark() {
    ADLCollectorTheme(darkTheme = true) {
        LoadingScreen()
    }
}