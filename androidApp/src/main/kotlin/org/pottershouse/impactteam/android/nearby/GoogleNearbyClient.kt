package org.pottershouse.impactteam.android.nearby

import android.content.Context
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class GoogleNearbyClient(
    context: Context,
    private val connections: ConnectionsClient = Nearby.getConnectionsClient(context.applicationContext),
) : NearbyClient {
    private val mutableEvents = MutableSharedFlow<NearbyClientEvent>(extraBufferCapacity = 64)
    override val events: Flow<NearbyClientEvent> = mutableEvents.asSharedFlow()

    private val connectionCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            mutableEvents.tryEmit(
                NearbyClientEvent.ConnectionInitiated(
                    endpointId = endpointId,
                    endpointName = info.endpointName,
                    authenticationToken = info.authenticationDigits,
                ),
            )
        }

        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
            if (resolution.status.isSuccess) {
                mutableEvents.tryEmit(NearbyClientEvent.Connected(endpointId))
            } else {
                mutableEvents.tryEmit(
                    NearbyClientEvent.Failure(endpointId, resolution.status.statusMessage ?: "Connection failed"),
                )
            }
        }

        override fun onDisconnected(endpointId: String) {
            mutableEvents.tryEmit(NearbyClientEvent.Disconnected(endpointId))
        }
    }

    private val discoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            mutableEvents.tryEmit(NearbyClientEvent.EndpointFound(endpointId, info.endpointName))
        }

        override fun onEndpointLost(endpointId: String) {
            mutableEvents.tryEmit(NearbyClientEvent.EndpointLost(endpointId))
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val bytes = payload.asBytes()
            if (payload.type == Payload.Type.BYTES && bytes != null) {
                mutableEvents.tryEmit(NearbyClientEvent.BytesReceived(endpointId, bytes))
            } else {
                mutableEvents.tryEmit(NearbyClientEvent.Failure(endpointId, "Only byte payloads are supported"))
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) = Unit
    }

    override suspend fun startAdvertising(config: NearbyStartConfig) {
        connections.startAdvertising(
            config.endpointName,
            config.serviceId,
            connectionCallback,
            AdvertisingOptions.Builder().setStrategy(config.strategy.toGoogle()).build(),
        ).reportFailure(null, "Advertising failed")
    }

    override suspend fun startDiscovery(config: NearbyStartConfig) {
        connections.startDiscovery(
            config.serviceId,
            discoveryCallback,
            DiscoveryOptions.Builder().setStrategy(config.strategy.toGoogle()).build(),
        ).reportFailure(null, "Discovery failed")
    }

    override suspend fun requestConnection(endpointName: String, endpointId: String) {
        connections.requestConnection(endpointName, endpointId, connectionCallback)
            .reportFailure(endpointId, "Connection request failed")
    }

    override suspend fun acceptConnection(endpointId: String) {
        connections.acceptConnection(endpointId, payloadCallback)
            .reportFailure(endpointId, "Connection acceptance failed")
    }

    override suspend fun sendBytes(endpointId: String, bytes: ByteArray) {
        connections.sendPayload(endpointId, Payload.fromBytes(bytes))
            .reportFailure(endpointId, "Payload send failed")
    }

    override suspend fun stopAdvertising() = connections.stopAdvertising()

    override suspend fun stopDiscovery() = connections.stopDiscovery()

    override suspend fun stopAllEndpoints() = connections.stopAllEndpoints()

    private fun com.google.android.gms.tasks.Task<Void>.reportFailure(endpointId: String?, prefix: String) {
        addOnFailureListener { error ->
            mutableEvents.tryEmit(
                NearbyClientEvent.Failure(endpointId, "$prefix: ${error.message ?: error::class.simpleName}"),
            )
        }
    }

    private fun NearbyStrategy.toGoogle(): Strategy = when (this) {
        NearbyStrategy.P2P_CLUSTER -> Strategy.P2P_CLUSTER
    }
}
