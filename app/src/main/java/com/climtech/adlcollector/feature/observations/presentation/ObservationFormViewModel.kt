package com.climtech.adlcollector.feature.observations.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.climtech.adlcollector.BuildConfig
import com.climtech.adlcollector.core.model.TenantConfig
import com.climtech.adlcollector.core.util.Result
import com.climtech.adlcollector.feature.observations.data.ObservationsRepository
import com.climtech.adlcollector.feature.observations.domain.FixedLocalConfig
import com.climtech.adlcollector.feature.observations.domain.ScheduleMode
import com.climtech.adlcollector.feature.observations.domain.SchedulePolicy
import com.climtech.adlcollector.feature.observations.domain.WindowedOnlyConfig
import com.climtech.adlcollector.feature.stations.data.StationsRepository
import com.climtech.adlcollector.feature.stations.data.net.StationDetail
import com.squareup.moshi.Moshi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import javax.inject.Inject


data class ObservationVariableUi(
    val id: Long,
    val name: String,
    val unit: String,
    val isRainfall: Boolean,
    val rangeCheck: StationDetail.RangeCheck? = null,
    val valueText: String = "",
    val rangeError: String? = null
)

data class ObservationFormUi(
    val loading: Boolean = true,
    val stationName: String = "",
    val timezone: String = "",
    val variables: List<ObservationVariableUi> = emptyList(),
    val displayLocal: LocalDateTime = LocalDateTime.now(),
    val late: Boolean = false,
    val locked: Boolean = false,
    val valid: Boolean = false,
    val reason: String? = null,
    val error: String? = null,
    val submitting: Boolean = false,
    val hasRangeErrors: Boolean = false
)

sealed class UiEvent {
    data class ShowToast(val message: String) : UiEvent()
}

@HiltViewModel
class ObservationFormViewModel @Inject constructor(
    private val stationsRepo: StationsRepository,
    private val observationsRepo: ObservationsRepository
) : ViewModel() {

    private val _ui = MutableStateFlow(ObservationFormUi())
    val ui = _ui.asStateFlow()

    // one-shot events (toasts, navigation, etc.)
    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    private lateinit var tenant: TenantConfig
    private var stationId: Long = -1
    private lateinit var submitEndpoint: String

    private var station: StationDetail? = null
    private var schedule: ScheduleMode? = null
    private var tz: ZoneId = ZoneOffset.UTC
    private var streamJob: Job? = null // track job

    fun start(
        tenant: TenantConfig, stationId: Long, stationName: String, submitEndpointUrl: String
    ) {
        this.tenant = tenant
        this.stationId = stationId
        this.submitEndpoint = submitEndpointUrl

        _ui.value = _ui.value.copy(loading = true, stationName = stationName, error = null)

        streamJob?.cancel() // cancel previous job
        streamJob = viewModelScope.launch {
            stationsRepo.stationDetailStream(tenant.id, stationId).collectLatest { d ->
                if (d == null) {
                    _ui.value = _ui.value.copy(loading = true)
                    return@collectLatest
                }
                station = d
                tz = ZoneId.of(d.timezone)
                schedule = buildSchedule(d)
                val vUi = d.variable_mappings.map {
                    ObservationVariableUi(
                        id = it.id,
                        name = it.adl_parameter_name,
                        unit = it.obs_parameter_unit,
                        isRainfall = it.is_rainfall,
                        rangeCheck = it.range_check
                    )
                }
                recomputeTime(null, vUi, d)
            }
        }
        // Also trigger a refresh (offline-safe; keeps cache on error)
        viewModelScope.launch { stationsRepo.refreshStationDetail(tenant, stationId) }
    }

    private fun buildSchedule(d: StationDetail): ScheduleMode = when (d.schedule.mode) {
        "fixed_local" -> {
            val c = d.schedule.config
            ScheduleMode.FixedLocal(
                FixedLocalConfig(
                    slots = (c["slots"] as List<*>).map { LocalTime.parse(it as String) },
                    windowBeforeMins = (c["window_before_mins"] as Number).toLong(),
                    windowAfterMins = (c["window_after_mins"] as Number).toLong(),
                    graceLateMins = (c["grace_late_mins"] as Number).toLong(),
                    roundingIncrementMins = (c["rounding_increment_mins"] as Number).toLong(),
                    backfillDays = (c["backfill_days"] as Number).toLong(),
                    allowFutureMins = (c["allow_future_mins"] as Number).toLong(),
                    lockAfterMins = (c["lock_after_mins"] as Number).toLong()
                )
            )
        }

        else -> {
            val c = d.schedule.config
            ScheduleMode.WindowedOnly(
                WindowedOnlyConfig(
                    windowStart = LocalTime.parse(c["window_start"] as String),
                    windowEnd = LocalTime.parse(c["window_end"] as String),
                    graceLateMins = (c["grace_late_mins"] as Number).toLong(),
                    roundingIncrementMins = (c["rounding_increment_mins"] as Number).toLong(),
                    backfillDays = (c["backfill_days"] as Number).toLong(),
                    allowFutureMins = (c["allow_future_mins"] as Number).toLong(),
                    lockAfterMins = (c["lock_after_mins"] as Number).toLong()
                )
            )
        }
    }

    fun onTimeChange(newLocal: LocalDateTime) {
        val d = station ?: return
        recomputeTime(newLocal, _ui.value.variables, d)
    }

    fun onVariableChanged(id: Long, newValueText: String) {
        val updated = _ui.value.variables.map { variable ->
            if (variable.id == id) {
                // Validate range if value is a valid number
                val rangeError = if (newValueText.isNotBlank()) {
                    val value = newValueText.toDoubleOrNull()
                    if (value != null && variable.rangeCheck != null) {
                        variable.rangeCheck.validate(value)
                    } else null
                } else null

                variable.copy(valueText = newValueText, rangeError = rangeError)
            } else variable
        }

        val hasRangeErrors = updated.any { it.rangeError != null }

        _ui.value = _ui.value.copy(
            variables = updated,
            hasRangeErrors = hasRangeErrors
        )
    }

    private fun recomputeTime(
        requestedLocal: LocalDateTime?, variablesUi: List<ObservationVariableUi>, d: StationDetail
    ) {
        val mode = schedule ?: return
        val now = Instant.now()
        val res = SchedulePolicy.validate(mode, tz, now, requestedLocal)
        _ui.value = _ui.value.copy(
            loading = false,
            stationName = d.name,
            timezone = d.timezone,
            variables = variablesUi,
            displayLocal = res.roundedLocal,
            late = res.late,
            locked = res.locked,
            valid = res.ok,
            reason = res.reason,
            error = null
        )
    }

    fun submit(duplicatePolicy: String? = "REVISION_WITH_REASON", reason: String? = null) {
        val state = _ui.value
        val d = station ?: run {
            _ui.value = state.copy(error = "Station detail not loaded")
            return
        }

        // Check for range errors before submitting
        if (state.hasRangeErrors) {
            _ui.value = state.copy(error = "Please fix value range errors before submitting")
            return
        }

        if (!state.valid || state.locked) {
            _ui.value = state.copy(error = state.reason ?: "Invalid time")
            return
        }

        // Build "records" for server (variable_mapping_id + value)
        val records = state.variables.mapNotNull {
            val num = it.valueText.toDoubleOrNull()
            if (num == null) null else mapOf(
                "variable_mapping_id" to it.id, "value" to num
            )
        }

        viewModelScope.launch {
            _ui.value = state.copy(submitting = true, error = null)
            val isoObsUtc = SchedulePolicy.localToIsoZ(state.displayLocal, tz)

            val appVersion = BuildConfig.VERSION_NAME

            // Store minimal payload for repo to finalize on upload
            val payload = mapOf(
                "records" to records, "metadata" to mapOf(
                    "late" to state.late,
                    "duplicate_policy" to duplicatePolicy,
                    "reason" to reason,
                    "app_version" to appVersion
                )
            )
            val payloadJson = Moshi.Builder().build().adapter(Map::class.java).toJson(payload)

            val res = observationsRepo.queueSubmit(
                tenant = tenant,
                stationId = d.id, // becomes station_link_id at upload time
                stationName = d.name,
                timezone = d.timezone,
                scheduleMode = d.schedule.mode,
                obsIsoUtc = isoObsUtc,
                payloadJson = payloadJson,
                late = state.late,
                locked = state.locked
            )

            // turn off spinner
            _ui.value = state.copy(submitting = false, error = (res as? Result.Err)?.error?.message)

            // emit toast on success
            if (res is Result.Ok) {
                _events.tryEmit(UiEvent.ShowToast("Observation queued for upload"))
            }
        }
    }

    fun getSubmitEndpoint(): String = submitEndpoint
    fun tenantId(): String = tenant.id

    // cleanup
    override fun onCleared() {
        super.onCleared()
        streamJob?.cancel()
    }

}