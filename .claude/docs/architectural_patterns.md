# Architectural Patterns

Patterns observed across multiple files in this codebase.

---

## 1. Offline-First Repository (Cache + Network)

**Files:** `StationsRepository.kt`, `ObservationsRepository.kt`, `StationsViewModel.kt`, `ObservationsViewModel.kt`

Every data-backed feature exposes two methods:
- A Room `Flow` for instant offline reads (UI subscribes immediately on `start()`)
- A `suspend fun refresh*()` that hits the network and writes results to Room, which re-emits via the Flow

On network failure the cache is **never cleared** — only the `Result.Err` is returned so the VM can set `isOffline = true` while still showing stale data. The VM shows an error only when there is no cached data to fall back on.

```
ViewModel.start(tenant)
  └── repo.stationsStream(tenantId) → Flow<List<Station>>   // Room emits immediately
  └── repo.refreshStations(tenant) → Result<Unit>           // network → Room → Flow re-emits
```

See: `StationsRepository.kt:60–65`, `StationsViewModel.kt:36–56`

---

## 2. Custom `Result<T>` Type

**File:** `core/util/Result.kt`

`sealed interface Result<out T>` with `Ok<T>` and `Err(Throwable)`. **Do not use Kotlin's stdlib `kotlin.Result`**. This type propagates through every repository return type and ViewModel handler. It provides `.map()`, `.flatMap()`, `.onSuccess()`, `.onFailure()`, `.getOrNull()`.

---

## 3. Sealed `NetworkException` Hierarchy

**File:** `core/net/NetworkException.kt`

All network errors map into this sealed class before reaching ViewModels:
`Unauthorized`, `Forbidden`, `NotFound`, `Client(code, body)`, `Server(code, body)`, `EmptyBody`, `Offline`, `Timeout`, `Serialization`, `UnexpectedBody`, `LoginRedirect`, `Unknown`.

The mapping happens in `core/util/RetrofitExt.kt` (`Response<T>.asResult()`) and in each repository's `catch` blocks. ViewModels pattern-match on these types to produce user-friendly strings.

---

## 4. `Response<T>.asResult()` Extension

**File:** `core/util/RetrofitExt.kt:12`

Converts any Retrofit `Response<T>` to `Result<T>`, mapping HTTP status codes to `NetworkException` variants and catching `UnknownHostException`, `SocketTimeoutException`, `JsonDataException`, and general `IOException`. Always re-throws `CancellationException`.

---

## 5. `retryNetwork { }` Suspend Helper

**File:** `core/util/Retry.kt`

```kotlin
retryNetwork(maxAttempts = 3) { /* block returning Result<T> */ }
```

Retries on `NetworkException.Timeout`, `NetworkException.Server` (5xx), and HTTP 429. Uses exponential backoff with ±50% jitter. Used in `StationsRepository.refreshStations()`. Not used in observation uploads (WorkManager handles that retry loop instead).

---

## 6. Per-Tenant Retrofit Instance

**Files:** `StationsRepository.kt:40–52`, `ObservationsRepository.kt:42–51`

There is **no singleton authenticated Retrofit**. Each repository builds its own `OkHttpClient` and `Retrofit` instance scoped to the current tenant via a private `apiFor(tenant: TenantConfig)` method. The `AuthInterceptor` lambda captures `authManager.getValidAccessToken(tenant)`.

OkHttp interceptor chain per call:
1. `AuthInterceptor` — adds `Authorization: Bearer <token>` (calls `AuthManager.getValidAccessToken`)
2. `ApiGuardInterceptor` — adds `Accept: application/json`, rejects HTML responses before Moshi sees them
3. `TokenAuthenticator` — handles 401 by triggering a refresh and retrying once

`NetworkModule` uses `https://placeholder.invalid/` as the Retrofit base URL because all calls use absolute `@Url` parameters derived from `TenantConfig.api(...)`.

---

## 7. UiState / StateFlow / HiltViewModel Convention

**Files:** `StationsViewModel.kt`, `ObservationsViewModel.kt`, `ObservationFormViewModel.kt`, `StationDetailViewModel.kt`

Every feature follows the same structure:
```kotlin
data class FooUiState(val loading: Boolean = false, val items: List<T> = emptyList(), val error: String? = null)

@HiltViewModel
class FooViewModel @Inject constructor(private val repo: FooRepository) : ViewModel() {
    private val _state = MutableStateFlow(FooUiState())
    val state: StateFlow<FooUiState> = _state.asStateFlow()
    // ...
}
```

UiState is a plain `data class` (not a sealed class), using nullable fields for optional state. VMs mutate via `_state.update { it.copy(...) }`.

---

## 8. Tenant-Scoped Composite DB Keys

**Files:** `ObservationEntity.kt`, `StationEntity.kt`, `StationDetailEntity.kt`, `StationsRepository.kt`

All Room entities use string composite primary keys to partition data by tenant:

| Entity | Primary key format |
|---|---|
| `StationEntity` | `"${tenantId}:${stationId}"` |
| `StationDetailEntity` | `"${tenantId}:${stationId}"` |
| `ObservationEntity` | `"${tenantId}:${stationId}:${obsTimeUtcMs}"` |

All DAO queries always filter by `tenantId`. DAOs provide `clearForTenant(tenantId)` for logout.

---

## 9. TenantConfig URL Builder

**File:** `core/model/TenantConfig.kt`

`TenantConfig` is sourced from Firestore and owns all URL construction. Use `.api(vararg segments)` or `.endpoint(vararg segments)` to build API URLs:
```kotlin
tenant.api("plugins", "api", "adl-collector", "manual-obs", "submit", trailingSlash = true)
```
These methods join segments safely over whatever base path is in `baseUrl`. Do not concatenate strings manually.

---

## 10. Sealed `Route` Class with `build()` Methods

**File:** `app/Routes.kt`

Navigation destinations are defined as `sealed class Route(val route: String)` with companion `build()` functions that URL-encode arguments via `Uri.encode()`. Always use `Route.Foo.build(...)` when navigating, never construct route strings by hand.

---

## 11. WorkManager Background Sync (`@HiltWorker`)

**File:** `feature/observations/sync/UploadObservationsWorker.kt`

`UploadObservationsWorker` is a `@HiltWorker` / `@AssistedInject` `CoroutineWorker`. It:
- Checks network suitability and battery optimization before uploading
- Processes observations in batches (default 10, max 50)
- Distinguishes permanent failures (4xx, bad JSON) from retriable ones (network, 5xx)
- Uses WorkManager's built-in exponential backoff (`BackoffPolicy.EXPONENTIAL`)
- Tags work with `"upload_observations"` so the VM can monitor progress via `getWorkInfosByTagFlow()`

Enqueue via `UploadObservationsWorker.createWorkRequest(tenantId, endpointUrl, isUrgent, allowMetered)`.

---

## 12. `AuthManager` Token Refresh with Mutex

**File:** `core/auth/AuthManager.kt`

`getValidAccessToken(tenant)` checks expiry with a 60-second skew before using a `Mutex` to prevent concurrent refresh storms. The double-check pattern inside `withLock` ensures only one coroutine performs the actual token refresh even if multiple callers race.
