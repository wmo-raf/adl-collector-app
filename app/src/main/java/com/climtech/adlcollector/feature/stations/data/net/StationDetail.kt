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
        val is_rainfall: Boolean,
        val range_check: RangeCheck? = null
    )

    @JsonClass(generateAdapter = true)
    data class RangeCheck(
        val min: Double? = null, val max: Double? = null, val inclusive: Boolean = true
    ) {
        /**
         * Validates if a value is within the allowed range
         * @return null if valid, error message if invalid
         */
        fun validate(value: Double): String? {
            return when {
                min != null && (if (inclusive) value < min else value <= min) -> {
                    "Value must be ${if (inclusive) "at least" else "greater than"} ${min.formatClean()}"
                }

                max != null && (if (inclusive) value > max else value >= max) -> {
                    "Value must be ${if (inclusive) "at most" else "less than"} ${max.formatClean()}"
                }

                else -> null
            }
        }

        /**
         * Returns a user-friendly description of the range
         */
        fun getDescription(): String? {
            return when {
                min != null && max != null -> {
                    if (inclusive) {
                        "Must be between ${min.formatClean()} and ${max.formatClean()} (inclusive)"
                    } else {
                        "Must be greater than ${min.formatClean()} and less than ${max.formatClean()}"
                    }
                }

                min != null -> {
                    if (inclusive) {
                        "Must be at least ${min.formatClean()}"
                    } else {
                        "Must be greater than ${min.formatClean()}"
                    }
                }

                max != null -> {
                    if (inclusive) {
                        "Must be at most ${max.formatClean()}"
                    } else {
                        "Must be less than ${max.formatClean()}"
                    }
                }

                else -> null
            }
        }
    }

    @JsonClass(generateAdapter = true)
    data class Schedule(
        val mode: String,                 // "fixed_local" | "windowed_only"
        val config: Map<String, Any?>     // keep generic; we’ll parse within UI/VM as needed
    )
}

/**
 * Formats a number to show only significant decimals
 */
private fun Double.formatClean(): String {
    return if (this % 1.0 == 0.0) {
        this.toInt().toString()
    } else {
        this.toString()
    }
}
