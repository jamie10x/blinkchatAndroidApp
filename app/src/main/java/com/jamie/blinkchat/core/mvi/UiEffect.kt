package com.jamie.blinkchat.core.mvi

/**
 * A marker interface for all one-time side effects in the MVI architecture.
 * Represents events that the ViewModel wants the UI to perform once,
 * such as navigation, showing a Toast/Snackbar, or displaying a dialog.
 * These are typically consumed by the UI and should not be part of the [UiState].
 * Implementations are typically sealed classes/interfaces specific to each feature.
 */
interface UiEffect