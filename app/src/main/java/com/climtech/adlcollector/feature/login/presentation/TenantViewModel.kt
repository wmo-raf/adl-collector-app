package com.climtech.adlcollector.feature.login.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.climtech.adlcollector.core.auth.TenantLocalStore
import com.climtech.adlcollector.core.model.TenantConfig
import com.climtech.adlcollector.feature.login.data.TenantRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TenantState(
    val loading: Boolean = false,
    val tenants: List<TenantConfig> = emptyList(),
    val selectedTenantId: String? = null,
    val error: String? = null
)

sealed class TenantIntent {
    data object LoadTenants : TenantIntent()
    data object RefreshTenants : TenantIntent()
    data class SelectTenant(val tenantId: String) : TenantIntent()
    data object ClearError : TenantIntent()
}

@HiltViewModel
class TenantViewModel @Inject constructor(
    private val tenantRepository: TenantRepository,
    private val tenantLocalStore: TenantLocalStore
) : ViewModel() {

    private val _tenantState = MutableStateFlow(TenantState())
    val tenantState: StateFlow<TenantState> = _tenantState.asStateFlow()

    init {
        // Load saved tenant selection
        viewModelScope.launch {
            tenantLocalStore.selectedTenantId.collect { savedId ->
                _tenantState.value = _tenantState.value.copy(selectedTenantId = savedId)
            }
        }

        // Load tenants on init
        handle(TenantIntent.LoadTenants)
    }

    fun handle(intent: TenantIntent) {
        when (intent) {
            is TenantIntent.LoadTenants -> loadTenants(preserveSelection = true)
            is TenantIntent.RefreshTenants -> loadTenants(preserveSelection = true)
            is TenantIntent.SelectTenant -> selectTenant(intent.tenantId)
            is TenantIntent.ClearError -> clearError()
        }
    }

    private fun loadTenants(preserveSelection: Boolean) {
        viewModelScope.launch {
            _tenantState.value = _tenantState.value.copy(loading = true, error = null)

            try {
                val allTenants = tenantRepository.listTenants()
                val visibleTenants = allTenants.filter { it.visible }

                val currentSelectedId =
                    if (preserveSelection) _tenantState.value.selectedTenantId else null
                val selectedId = when {
                    currentSelectedId != null && visibleTenants.any { it.id == currentSelectedId } -> currentSelectedId
                    visibleTenants.isNotEmpty() -> visibleTenants.first().id
                    else -> null
                }

                _tenantState.value = _tenantState.value.copy(
                    loading = false,
                    tenants = visibleTenants,
                    selectedTenantId = selectedId,
                    error = null
                )

                // Update selected tenant in local store if it changed
                if (selectedId != currentSelectedId && selectedId != null) {
                    tenantLocalStore.saveSelectedTenantId(selectedId)
                    val tenant = visibleTenants.first { it.id == selectedId }
                    tenantLocalStore.saveTenantConfig(tenant)
                }

            } catch (e: Exception) {
                _tenantState.value = _tenantState.value.copy(
                    loading = false,
                    error = "Failed to load ADL Instances: ${e.message}"
                )
            }
        }
    }

    private fun selectTenant(tenantId: String) {
        val tenant = _tenantState.value.tenants.firstOrNull { it.id == tenantId }
        if (tenant == null) return

        viewModelScope.launch {
            tenantLocalStore.saveSelectedTenantId(tenantId)
            tenantLocalStore.saveTenantConfig(tenant)
            _tenantState.value = _tenantState.value.copy(selectedTenantId = tenantId)
        }
    }

    private fun clearError() {
        _tenantState.value = _tenantState.value.copy(error = null)
    }

    fun getSelectedTenant(): TenantConfig? {
        val selectedId = _tenantState.value.selectedTenantId
        return _tenantState.value.tenants.firstOrNull { it.id == selectedId }
    }
}