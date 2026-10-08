package com.scr01.mod

internal enum class UiOperationPhase { IDLE, RUNNING, SUCCEEDED, FAILED, CANCELLED, UNKNOWN }
internal data class UiOperationState(
    val phase: UiOperationPhase = UiOperationPhase.IDLE,
    val message: String = "",
) {
    val running: Boolean get() = phase == UiOperationPhase.RUNNING
    val hasError: Boolean get() = phase == UiOperationPhase.FAILED || phase == UiOperationPhase.UNKNOWN
}
