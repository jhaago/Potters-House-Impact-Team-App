package org.pottershouse.impactteam.android.ui

import org.pottershouse.impactteam.state.SeparationLevel

data class SeparationFieldTestState(
    val memberId: String? = null,
    val startedAtEpochMillis: Long? = null,
    val withinRangeAtEpochMillis: Long? = null,
    val watchingAtEpochMillis: Long? = null,
    val warningAtEpochMillis: Long? = null,
    val seriousAtEpochMillis: Long? = null,
    val recoveredAtEpochMillis: Long? = null,
    val uniqueGpsFixCount: Int = 0,
    val latestFixAgeSeconds: Long? = null,
    val latestAccuracyMeters: Int? = null,
    val latestSeparationDistanceMeters: Int? = null,
) {
    val isActive: Boolean
        get() = memberId != null && startedAtEpochMillis != null

    val isComplete: Boolean
        get() = withinRangeAtEpochMillis != null &&
            watchingAtEpochMillis != null &&
            warningAtEpochMillis != null &&
            seriousAtEpochMillis != null &&
            recoveredAtEpochMillis != null

    fun redactedSummary(): String {
        val started = startedAtEpochMillis
        fun elapsed(timestamp: Long?): String = when {
            timestamp == null || started == null -> "pending"
            else -> "${((timestamp - started).coerceAtLeast(0L) / 1_000L)} sec"
        }
        fun latest(value: Int?, suffix: String): String = value?.let { "$it $suffix" } ?: "unknown"

        return buildString {
            appendLine("Impact Team separation field test")
            appendLine("Member: ${memberId ?: "None"}")
            appendLine("Status: ${if (isComplete) "Complete" else "Incomplete"}")
            appendLine("Within range: ${elapsed(withinRangeAtEpochMillis)}")
            appendLine("Watching: ${elapsed(watchingAtEpochMillis)}")
            appendLine("WARNING: ${elapsed(warningAtEpochMillis)}")
            appendLine("SERIOUS: ${elapsed(seriousAtEpochMillis)}")
            appendLine("Recovered: ${elapsed(recoveredAtEpochMillis)}")
            appendLine("GPS fixes observed: $uniqueGpsFixCount")
            appendLine("Latest fix age: ${latestFixAgeSeconds?.let { "$it sec" } ?: "unknown"}")
            appendLine("Latest accuracy: ${latestAccuracyMeters?.let { "±$it m" } ?: "unknown"}")
            append("Latest separation: ${latest(latestSeparationDistanceMeters, "m")}")
        }
    }
}

class SeparationFieldTestRecorder {
    private var state = SeparationFieldTestState()
    private var lastGpsFixId: String? = null

    fun start(memberId: String, startedAtEpochMillis: Long) {
        state = SeparationFieldTestState(
            memberId = memberId,
            startedAtEpochMillis = startedAtEpochMillis,
        )
        lastGpsFixId = null
    }

    fun observe(
        stableLevel: SeparationLevel,
        rawLevel: SeparationLevel,
        observedAtEpochMillis: Long,
        gpsFixId: String? = null,
        capturedAtEpochMillis: Long? = null,
        accuracyMeters: Int? = null,
        separationDistanceMeters: Int? = null,
    ) {
        if (!state.isActive) return

        var next = state

        if (gpsFixId != null) {
            if (gpsFixId != lastGpsFixId) {
                next = next.copy(uniqueGpsFixCount = next.uniqueGpsFixCount + 1)
                lastGpsFixId = gpsFixId
            }
            next = next.copy(
                latestFixAgeSeconds = capturedAtEpochMillis?.let {
                    ((observedAtEpochMillis - it).coerceAtLeast(0L) / 1_000L)
                },
                latestAccuracyMeters = accuracyMeters,
                latestSeparationDistanceMeters = separationDistanceMeters,
            )
        }

        if (
            next.withinRangeAtEpochMillis == null &&
            stableLevel == SeparationLevel.CLEAR &&
            rawLevel == SeparationLevel.CLEAR
        ) {
            next = next.copy(withinRangeAtEpochMillis = observedAtEpochMillis)
        }

        if (
            next.watchingAtEpochMillis == null &&
            (rawLevel == SeparationLevel.WARNING || rawLevel == SeparationLevel.SERIOUS)
        ) {
            next = next.copy(watchingAtEpochMillis = observedAtEpochMillis)
        }

        if (
            next.warningAtEpochMillis == null &&
            stableLevel == SeparationLevel.WARNING
        ) {
            next = next.copy(warningAtEpochMillis = observedAtEpochMillis)
        }

        if (
            next.seriousAtEpochMillis == null &&
            stableLevel == SeparationLevel.SERIOUS
        ) {
            next = next.copy(seriousAtEpochMillis = observedAtEpochMillis)
        }

        if (
            next.seriousAtEpochMillis != null &&
            next.recoveredAtEpochMillis == null &&
            stableLevel == SeparationLevel.CLEAR &&
            rawLevel == SeparationLevel.CLEAR
        ) {
            next = next.copy(recoveredAtEpochMillis = observedAtEpochMillis)
        }

        state = next
    }

    fun snapshot(): SeparationFieldTestState = state

    fun reset() {
        state = SeparationFieldTestState()
        lastGpsFixId = null
    }
}
