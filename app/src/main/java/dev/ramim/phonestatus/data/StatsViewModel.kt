package dev.ramim.phonestatus.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

class StatsViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx = app.applicationContext

    /** Battery, RAM, storage, display: refreshed every second while the screen is visible. */
    val stats: StateFlow<Stats> = flow {
        while (true) {
            emit(StatsReader.readAll(ctx))
            delay(1_000)
        }
    }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Stats())

    /** Thermal sensors (root): its own loop so a slow `su` never freezes the rest. */
    val thermal: StateFlow<ThermalInfo> = flow {
        while (true) {
            emit(ThermalReader.read())
            delay(1_000)
        }
    }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThermalInfo())

    fun retryRoot() = ThermalReader.resetRoot()
}
