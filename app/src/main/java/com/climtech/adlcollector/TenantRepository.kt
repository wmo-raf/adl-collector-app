package com.climtech.adlcollector

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class TenantRepository {
    private val db = FirebaseFirestore.getInstance()

    suspend fun listTenants(): List<TenantConfig> = suspendCancellableCoroutine { cont ->
        db.collection("tenants").get().addOnSuccessListener { qs ->
            val items = qs.documents.mapNotNull { d ->
                runCatching {
                    TenantConfig(
                        id = d.id,
                        name = d.getString("name") ?: d.id,
                        authorizeEndpoint = d.getString("authorize_endpoint")!!,
                        tokenEndpoint = d.getString("token_endpoint")!!,
                        clientId = d.getString("client_id")!!,
                        scopes = (d.get("scopes") as? List<*>)?.filterIsInstance<String>()
                            ?: listOf("adl.read", "adl.write")
                    )
                }.getOrNull()
            }
            cont.resume(items)
        }.addOnFailureListener { e -> cont.resumeWithException(e) }
    }
}
