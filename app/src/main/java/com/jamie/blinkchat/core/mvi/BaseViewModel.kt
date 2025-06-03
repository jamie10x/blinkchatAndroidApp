package com.jamie.blinkchat.core.mvi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Base ViewModel class for MVI architecture.
 *
 * @param S The type of the [UiState].
 * @param I The type of the [UiIntent].
 * @param E The type of the [UiEffect].
 */
abstract class BaseViewModel<S : UiState, I : UiIntent, E : UiEffect> : ViewModel() {

    // MutableStateFlow to hold the current UI state.
    // It's private to prevent direct modification from outside the ViewModel.
    private val _uiState: MutableStateFlow<S> by lazy { MutableStateFlow(createInitialState()) }

    /**
     * Publicly exposed, immutable [StateFlow] for observing UI state changes.
     * Views should collect this to update themselves.
     */
    val uiState: StateFlow<S> = _uiState.asStateFlow()

    // Channel for emitting one-time UI effects.
    // Channels are well-suited for events that should be consumed exactly once.
    private val _effect: Channel<E> = Channel()

    /**
     * Publicly exposed [Flow] for observing UI effects.
     * Views should collect this to handle one-time actions like navigation or showing toasts.
     */
    val effect = _effect.receiveAsFlow()


    // Alternative for effects using SharedFlow (can be useful if multiple collectors are needed, or for replaying last effect)
    // private val _effect: MutableSharedFlow<E> = MutableSharedFlow(replay = 0, extraBufferCapacity = Channel.UNLIMITED)
    // val effect = _effect.asSharedFlow()


    /**
     * Abstract method to be implemented by subclasses to provide the initial UI state.
     */
    abstract fun createInitialState(): S

    /**
     * Abstract method to be implemented by subclasses to handle incoming UI intents.
     * This is where the core logic for processing user actions and updating state resides.
     */
    abstract fun handleIntent(intent: I)

    /**
     * Sends a [UiIntent] to the ViewModel for processing.
     * This is typically called by the View (Composable screen).
     */
    fun setIntent(intent: I) {
        handleIntent(intent)
    }

    /**
     * Protected method for subclasses to update the [UiState].
     * It ensures state updates are managed within the ViewModel.
     */
    protected fun setState(reduce: S.() -> S) {
        _uiState.value = uiState.value.reduce()
    }

    /**
     * Protected method for subclasses to send a [UiEffect] to the UI.
     * Uses [viewModelScope] to launch a coroutine for sending the effect via the channel.
     */
    protected fun setEffect(builder: () -> E) {
        val effectValue = builder()
        viewModelScope.launch {
            _effect.send(effectValue)
        }
    }

    // Example of how you might use SharedFlow for effects if preferred:
    /*
    protected fun setEffectShared(builder: () -> E) {
        val effectValue = builder()
        viewModelScope.launch {
            _effect.tryEmit(effectValue) // For SharedFlow
        }
    }
    */
}