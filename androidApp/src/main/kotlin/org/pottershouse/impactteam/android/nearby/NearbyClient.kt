package org.pottershouse.impactteam.android.nearby

import kotlinx.coroutines.flow.Flow

enum class NearbyStrategy {
    P2P_CLUSTER,
}

data class NearbyStartConfig(
    val endpointName: String,
    val serviceId: String,
    val strategy: NearbyStrategy,
)

sealed interface NearbyClientEvent {
    data class EndpointFound(val endpointId: String, val endpointName: String) : NearbyClientEvent
    data class EndpointLost(val endpointId: String) : NearbyClientEvent
    data class ConnectionInitiated(
        val endpointId: String,
        val endpointName: String,
        val authenticationToken: String,
    ) : NearbyClientEvent
    data class Connected(val endpointId: String) : NearbyClientEvent
    data class Disconnected(val endpointId: String) : NearbyClientEvent
    data class BytesReceived(val endpointId: String, val bytes: ByteArray) : NearbyClientEvent
    data class Failure(val endpointId: String?, val reason: String) : NearbyClientEvent
}

interface NearbyClient {
    val events: Flow<NearbyClientEvent>

    suspend fun startAdvertising(config: NearbyStartConfig)
    suspend fun startDiscovery(config: NearbyStartConfig)
    suspend fun requestConnection(endpointName: String, endpointId: String)
    suspend fun acceptConnection(endpointId: String)
    suspend fun sendBytes(endpointId: String, bytes: ByteArray)
    suspend fun stopAdvertising()
    suspend fun stopDiscovery()
    suspend fun stopAllEndpoints()
}
