package com.jamie.blinkchat.core.mvi

/**
 * A marker interface for all user intents or actions in the MVI architecture.
 * Represents actions triggered by the user (e.g., button clicks, text input)
 * or other events that the ViewModel needs to process.
 * Implementations are typically sealed classes/interfaces specific to each feature.
 */
interface UiIntent