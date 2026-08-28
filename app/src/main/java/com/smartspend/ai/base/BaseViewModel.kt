package com.smartspend.ai.base

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

abstract class BaseViewModel : ViewModel() {
    // Using SharedFlow for navigation events to ensure one-time emission and avoid unintended re-navigation.
    protected val _navigationEvent =
        MutableSharedFlow<NavigationEvent>(replay = 0, extraBufferCapacity = 1)
    val navigationEvent: SharedFlow<NavigationEvent> = _navigationEvent.asSharedFlow()

    abstract fun onEvent(event: UiEvent)
}

abstract class UiEvent