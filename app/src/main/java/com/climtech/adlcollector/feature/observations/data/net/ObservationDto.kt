package com.climtech.adlcollector.feature.observations.data.net

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ObservationPostRequest(
    val idempotency_key: String,
    val submission_time: String,
    val observation_time: String,
    val station_link_id: Long,
    val records: List<Record>,
    val metadata: Metadata
) {
    @JsonClass(generateAdapter = true)
    data class Record(
        val variable_mapping_id: Long, val value: Double
    )

    @JsonClass(generateAdapter = true)
    data class Metadata(
        val late: Boolean? = null,
        val duplicate_policy: String? = null,  // "REVISION_WITH_REASON" etc.
        val reason: String? = null,
        val app_version: String
    )
}

@JsonClass(generateAdapter = true)
data class ObservationPostResponse(
    val id: Long,
    val station_link_id: Long,
    val observation_time: String,
    val status: String // e.g., "accepted","late","revised"
)
