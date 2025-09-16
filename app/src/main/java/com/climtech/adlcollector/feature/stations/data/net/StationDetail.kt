package com.climtech.adlcollector.feature.stations.data.net

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class StationDetail(
    val id: Long,
    val name: String,
    val timezone: String,
    val variable_mappings: List<VariableMapping>,
    val schedule: Schedule
) {
    @JsonClass(generateAdapter = true)
    data class VariableMapping(
        val id: Long,
        val adl_parameter_name: String,
        val obs_parameter_unit: String,
        val is_rainfall: Boolean
    )

    @JsonClass(generateAdapter = true)
    data class Schedule(
        val mode: String,                 // "fixed_local" | "windowed_only"
        val config: Map<String, Any?>     // keep generic; we’ll parse within UI/VM as needed
    )
}