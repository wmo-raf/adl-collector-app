package com.climtech.adlcollector.feature.observations.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.abs

data class FixedLocalConfig(
    val slots: List<LocalTime>,
    val windowBeforeMins: Long,
    val windowAfterMins: Long,
    val graceLateMins: Long,
    val roundingIncrementMins: Long,
    val backfillDays: Long,
    val allowFutureMins: Long,
    val lockAfterMins: Long
)

data class WindowedOnlyConfig(
    val windowStart: LocalTime,
    val windowEnd: LocalTime,
    val graceLateMins: Long,
    val roundingIncrementMins: Long,
    val backfillDays: Long,
    val allowFutureMins: Long,
    val lockAfterMins: Long
)

sealed class ScheduleMode {
    data class FixedLocal(val cfg: FixedLocalConfig) : ScheduleMode()
    data class WindowedOnly(val cfg: WindowedOnlyConfig) : ScheduleMode()
}

data class ValidationResult(
    val ok: Boolean,
    val late: Boolean,
    val locked: Boolean,
    val reason: String? = null,
    val roundedLocal: LocalDateTime
)

object SchedulePolicy {

    /** Round to nearest N minutes */
    fun roundLocal(dt: LocalDateTime, incrementMin: Long): LocalDateTime {
        if (incrementMin <= 1) return dt.withSecond(0).withNano(0)
        val totalMin = dt.hour * 60 + dt.minute
        val rounded = ((totalMin + incrementMin / 2) / incrementMin) * incrementMin
        val hour = ((rounded / 60) % 24).toInt()
        val minute = (rounded % 60).toInt()
        return dt.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
    }

    /** For fixed slots: choose nearest slot today or yesterday depending on proximity. */
    fun nearestSlotLocal(nowLocal: LocalDateTime, slots: List<LocalTime>): LocalDateTime {
        if (slots.isEmpty()) return nowLocal
        val today = nowLocal.toLocalDate()
        val candidates = buildList {
            slots.forEach { add(LocalDateTime.of(today, it)) }
            // also consider yesterday/next for edge windows around midnight
            slots.forEach { add(LocalDateTime.of(today.minusDays(1), it)) }
            slots.forEach { add(LocalDateTime.of(today.plusDays(1), it)) }
        }
        return candidates.minBy { abs(Duration.between(nowLocal, it).toMinutes()) }
    }

    /**
     * Validate/normalize a local observation time against schedule rules.
     * Returns rounded time and flags (late/locked) with reason if invalid.
     */
    fun validate(
        mode: ScheduleMode,
        stationTz: ZoneId,
        nowUtc: Instant,
        requestedLocal: LocalDateTime? = null
    ): ValidationResult {
        val nowLocal = LocalDateTime.ofInstant(nowUtc, stationTz)

        return when (mode) {
            is ScheduleMode.FixedLocal -> {
                val cfg = mode.cfg
                val baseLocal = requestedLocal ?: nearestSlotLocal(nowLocal, cfg.slots)
                val rounded = roundLocal(baseLocal, cfg.roundingIncrementMins)

                // windows for the chosen slot
                val slot = rounded.toLocalTime()
                val slotLocal = LocalDateTime.of(rounded.toLocalDate(), slot)

                val open = slotLocal.minusMinutes(cfg.windowBeforeMins)
                val close = slotLocal.plusMinutes(cfg.windowAfterMins + cfg.graceLateMins)

                val late = nowLocal.isAfter(slotLocal.plusMinutes(cfg.windowAfterMins))
                val locked = nowLocal.isAfter(rounded.plusMinutes(cfg.lockAfterMins))

                // backfill: cannot be older than N days
                val minLocal = nowLocal.minusDays(cfg.backfillDays)
                // small future allowance
                val maxLocal = nowLocal.plusMinutes(cfg.allowFutureMins)

                val inWindow = nowLocal.isAfter(open) && nowLocal.isBefore(close)
                val withinBackfill = !rounded.isBefore(minLocal)
                val withinFuture = !rounded.isAfter(maxLocal)

                val ok = inWindow && withinBackfill && withinFuture && !locked
                val reason = when {
                    locked -> "Editing locked for this slot."
                    !withinBackfill -> "Older than allowed backfill window."
                    !withinFuture -> "Too far in the future."
                    !inWindow -> "Outside allowed submission window."
                    else -> null
                }
                ValidationResult(ok, late, locked, reason, rounded)
            }

            is ScheduleMode.WindowedOnly -> {
                val cfg = mode.cfg
                val baseLocal = requestedLocal ?: nowLocal
                val rounded = roundLocal(baseLocal, cfg.roundingIncrementMins)

                val date = rounded.toLocalDate()
                val winStart = LocalDateTime.of(date, cfg.windowStart)
                val winEnd = LocalDateTime.of(date, cfg.windowEnd)
                val close = winEnd.plusMinutes(cfg.graceLateMins)

                val late = rounded.isAfter(winEnd)
                val locked = nowLocal.isAfter(rounded.plusMinutes(cfg.lockAfterMins))

                val minLocal = nowLocal.minusDays(cfg.backfillDays)
                val maxLocal = nowLocal.plusMinutes(cfg.allowFutureMins)

                val inWindow = rounded.isAfter(winStart.minusSeconds(1)) && rounded.isBefore(
                    close.plusSeconds(1)
                )
                val withinBackfill = !rounded.isBefore(minLocal)
                val withinFuture = !rounded.isAfter(maxLocal)

                val ok = inWindow && withinBackfill && withinFuture && !locked
                val reason = when {
                    locked -> "Editing locked for this window."
                    !withinBackfill -> "Older than allowed backfill window."
                    !withinFuture -> "Too far in the future."
                    !inWindow -> "Outside allowed submission window."
                    else -> null
                }
                ValidationResult(ok, late, locked, reason, rounded)
            }
        }
    }

    /** Convert a local datetime (station tz) to ISO8601 Z. */
    fun localToIsoZ(local: LocalDateTime, zone: ZoneId): String =
        local.atZone(zone).withZoneSameInstant(ZoneOffset.UTC).toInstant().toString()
}
