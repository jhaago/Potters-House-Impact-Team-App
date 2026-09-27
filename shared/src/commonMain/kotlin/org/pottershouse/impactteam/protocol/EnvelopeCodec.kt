package org.pottershouse.impactteam.protocol

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

sealed interface DecodeResult {
    data class Success(val envelope: TrackingEnvelope) : DecodeResult
    data class UnsupportedProtocol(val protocolVersion: Int) : DecodeResult
    data class TooLarge(val encodedBytes: Int) : DecodeResult
    data class Malformed(val reason: String) : DecodeResult
}

@OptIn(ExperimentalSerializationApi::class)
object EnvelopeCodec {
    const val SUPPORTED_PROTOCOL_VERSION = 1
    const val MAX_ENCODED_BYTES = 16 * 1024

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun encode(envelope: TrackingEnvelope): String {
        val encoded = json.encodeToString(envelope)
        require(encoded.encodeToByteArray().size <= MAX_ENCODED_BYTES) {
            "Encoded tracking envelope exceeds $MAX_ENCODED_BYTES bytes"
        }
        return encoded
    }

    fun decode(encoded: String): DecodeResult {
        val byteCount = encoded.encodeToByteArray().size
        if (byteCount > MAX_ENCODED_BYTES) return DecodeResult.TooLarge(byteCount)

        return try {
            val element = json.parseToJsonElement(encoded)
            val version = element.jsonObject["protocol_version"]?.jsonPrimitive?.intOrNull
                ?: return DecodeResult.Malformed("protocol_version is missing or invalid")
            if (version != SUPPORTED_PROTOCOL_VERSION) {
                return DecodeResult.UnsupportedProtocol(version)
            }
            DecodeResult.Success(json.decodeFromJsonElement(TrackingEnvelope.serializer(), element))
        } catch (error: SerializationException) {
            DecodeResult.Malformed(error.message ?: "Invalid tracking envelope")
        } catch (error: IllegalArgumentException) {
            DecodeResult.Malformed(error.message ?: "Invalid tracking envelope")
        }
    }
}
