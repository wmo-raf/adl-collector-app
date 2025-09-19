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

        // Set initial state with saved selection
        _state.value = _state.value.copy(
            selectedTenantId = savedTenantId,
            isLoading = true
        )

        // Load tenants from network
        try {
            val allTenants = tenantRepository.listTenants()
            val visibleTenants = allTenants.filter { it.visible }

            // Determine final selection - prioritize saved selection if it's still valid
            val finalSelectedId = when {
                // If we have a saved selection and it's still available, keep it
                savedTenantId != null && visibleTenants.any { it.id == savedTenantId } -> {
                    savedTenantId
                }
                // If saved selection is invalid but we have tenants, pick first available
                visibleTenants.isNotEmpty() -> {
                    visibleTenants.first().id
                }
                // No tenants available
                else -> null
            }

            // Update state with final results
            _state.value = TenantState(
                isLoading = false,
                tenants = visibleTenants,
                selectedTenantId = finalSelectedId,
                error = null
            )

            // Persist the final selection if it changed
            if (finalSelectedId != null && finalSelectedId != savedTenantId) {
                selectTenant(finalSelectedId)
            }

        } catch (e: Exception) {
            // On error, keep the saved selection but show error
            _state.value = _state.value.copy(
                isLoading = false,
                error = "Failed to load ADL Instances: ${e.message}"
            )
        }
    }

    suspend fun loadTenants(preserveSelection: Boolean = true) {
        val currentState = _state.value
        val preservedSelectionId = if (preserveSelection) currentState.selectedTenantId else null

        _state.value = currentState.copy(isLoading = true, error = null)

        try {
            val allTenants = tenantRepository.listTenants()
            val visibleTenants = allTenants.filter { it.visible }

            // Selection logic
            val newSelectedId = when {
                // 1. Preserve existing selection if it's still valid
                preservedSelectionId != null && visibleTenants.any { it.id == preservedSelectionId } -> {
                    preservedSelectionId
                }
                // 2. If no preserved selection or it's invalid, pick first available
                visibleTenants.isNotEmpty() -> {
                    visibleTenants.first().id
                }
                // 3. No tenants available
                else -> null
            }

            _state.value = TenantState(
                isLoading = false,
                tenants = visibleTenants,
                selectedTenantId = newSelectedId,
                error = null
            )

            // Save selection if changed and valid
            if (newSelectedId != null && newSelectedId != preservedSelectionId) {
                selectTenant(newSelectedId)
            }

        } catch (e: Exception) {
            _state.value = currentState.copy(
                isLoading = false,
                error = "Failed to load ADL Instances: ${e.message}"
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