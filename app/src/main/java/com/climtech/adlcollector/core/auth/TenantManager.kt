package com.climtech.adlcollector.core.auth

import com.climtech.adlcollector.core.model.TenantConfig
import com.climtech.adlcollector.feature.login.data.TenantRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

data class TenantState(
    val isLoading: Boolean = false,
    val tenants: List<TenantConfig> = emptyList(),
    val selectedTenantId: String? = null,
    val error: String? = null
) {
    val selectedTenant: TenantConfig?
        get() = tenants.firstOrNull { it.id == selectedTenantId }
}

@Singleton
class TenantManager @Inject constructor(
    private val tenantRepository: TenantRepository, private val tenantLocalStore: TenantLocalStore
) {
    private val _state = MutableStateFlow(TenantState())
    val state: StateFlow<TenantState> = _state.asStateFlow()

    suspend fun initialize() {
        // Restore saved selection first
        val savedTenantId = tenantLocalStore.selectedTenantId.first()
        _state.value = _state.value.copy(selectedTenantId = savedTenantId)

        // Then load tenants
        loadTenants(preserveSelection = true)
    }

    suspend fun loadTenants(preserveSelection: Boolean = true) {
        val currentState = _state.value
        _state.value = currentState.copy(isLoading = true, error = null)

        val previouslySelectedId = if (preserveSelection) currentState.selectedTenantId else null

        try {
            val allTenants = tenantRepository.listTenants()
            val visibleTenants = allTenants.filter { it.visible }

            val newSelectedId = when {
                previouslySelectedId != null && visibleTenants.any { it.id == previouslySelectedId } -> previouslySelectedId

                else -> visibleTenants.firstOrNull()?.id
            }

            _state.value = TenantState(
                isLoading = false,
                tenants = visibleTenants,
                selectedTenantId = newSelectedId,
                error = null
            )

            // Save selection if changed
            if (newSelectedId != null && newSelectedId != previouslySelectedId) {
                selectTenant(newSelectedId)
            }

        } catch (e: Exception) {
            _state.value = currentState.copy(
                isLoading = false, error = "Failed to load ADL Instances: ${e.message}"
            )
        }
    }

    suspend fun selectTenant(tenantId: String) {
        val currentState = _state.value
        val tenant = currentState.tenants.firstOrNull { it.id == tenantId }

        if (tenant != null) {
            _state.value = currentState.copy(selectedTenantId = tenantId)

            // Persist selection
            tenantLocalStore.saveSelectedTenantId(tenantId)
            tenantLocalStore.saveTenantConfig(tenant)
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}