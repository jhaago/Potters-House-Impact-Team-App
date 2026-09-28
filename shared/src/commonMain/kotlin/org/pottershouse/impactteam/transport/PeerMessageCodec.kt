package org.pottershouse.impactteam.transport

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

sealed interface PeerMessageDecodeResult {
    data class Success(val message: PeerMessage) : PeerMessageDecodeResult
    data class TooLarge(val encodedBytes: Int) : PeerMessageDecodeResult
    data class Invalid(val reason: String) : PeerMessageDecodeResult
    data class UnsupportedProtocol(val protocolVersion: Int) : PeerMessageDecodeResult
}

@Serializable
private data class PeerFrame(
    @SerialName("protocol_version")
    val protocolVersion: Int,
    val message: PeerMessage,
)

object PeerMessageCodec {
    const val PROTOCOL_VERSION = 1
    const val MAX_PAYLOAD_BYTES = 64 * 1024
    const val MAX_BATCH_RECORDS = 50

    private val json = Json {
        classDiscriminator = "kind"
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun encode(message: PeerMessage): ByteArray {
        require(message !is PeerMessage.Batch || message.records.size <= MAX_BATCH_RECORDS) {
            "Peer batch exceeds $MAX_BATCH_RECORDS records"
        }
        val encoded = json.encodeToString(PeerFrame(PROTOCOL_VERSION, message)).encodeToByteArray()
        require(encoded.size <= MAX_PAYLOAD_BYTES) {
            "Peer payload exceeds $MAX_PAYLOAD_BYTES bytes"
        }
        return encoded
    }

    fun decode(encoded: ByteArray): PeerMessageDecodeResult {
        if (encoded.size > MAX_PAYLOAD_BYTES) return PeerMessageDecodeResult.TooLarge(encoded.size)
        return try {
            val element = json.parseToJsonElement(encoded.decodeToString())
            val version = element.jsonObject["protocol_version"]?.jsonPrimitive?.intOrNull
                ?: return PeerMessageDecodeResult.Invalid("protocol_version is missing or invalid")
            if (version != PROTOCOL_VERSION) {
                return PeerMessageDecodeResult.UnsupportedProtocol(version)
            }
            val frame = json.decodeFromJsonElement(PeerFrame.serializer(), element)
            if (frame.message is PeerMessage.Batch && frame.message.records.size > MAX_BATCH_RECORDS) {
                PeerMessageDecodeResult.Invalid("Peer batch exceeds $MAX_BATCH_RECORDS records")
            } else {
                PeerMessageDecodeResult.Success(frame.message)
            }
        } catch (error: SerializationException) {
            PeerMessageDecodeResult.Invalid(error.message ?: "Malformed peer payload")
        } catch (error: IllegalArgumentException) {
            PeerMessageDecodeResult.Invalid(error.message ?: "Malformed peer payload")
        }
    }
}
