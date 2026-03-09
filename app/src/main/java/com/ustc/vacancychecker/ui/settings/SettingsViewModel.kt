package com.ustc.vacancychecker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ustc.vacancychecker.data.local.CourseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: CourseRepository
) : ViewModel() {

    val monitoringInterval: StateFlow<Int> = repository.monitoringIntervalFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 15
        )

    fun updateInterval(interval: Int) {
        viewModelScope.launch {
            repository.updateMonitoringInterval(interval)
        }
    }
}
