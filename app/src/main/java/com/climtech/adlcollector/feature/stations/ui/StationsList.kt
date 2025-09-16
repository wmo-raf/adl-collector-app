package com.climtech.adlcollector.feature.stations.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.climtech.adlcollector.core.ui.theme.ADLCollectorTheme
import com.climtech.adlcollector.feature.stations.data.net.Station


@Composable
fun StationsList(
    itemsList: List<Station>, onOpenStation: (Long, String) -> Unit
) {
    if (itemsList.isEmpty()) {
        EmptyStationsState(onRefresh = null)
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(
            items = itemsList, key = { it.id }) { s ->
            StationRow(
                station = s,
                modifier = Modifier
                    .fillMaxWidth()
                    .animateItem(),
                subtitle = "ID: ${s.id}",
                showAssignedChip = false,
                updatedAgo = null,
                statusColor = null,
                onClick = { onOpenStation(s.id, s.name) })
        }
    }
}


@Preview(showBackground = true, name = "StationsList • Sample")
@Composable
private fun PreviewStationsList() {
    ADLCollectorTheme {
        val sampleStations = listOf(
            Station(id = 101, name = "Nairobi West Substation"),
            Station(id = 202, name = "Kisumu Bay Station"),
            Station(id = 303, name = "Mombasa Port Station")
        )
        StationsList(
            itemsList = sampleStations,
            onOpenStation = { id, name ->
                // no-op for preview
            }
        )
    }
}