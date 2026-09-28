package org.pottershouse.impactteam.transport

import org.pottershouse.impactteam.domain.DeviceId
import org.pottershouse.impactteam.domain.RecordId
import org.pottershouse.impactteam.protocol.locationEnvelope
import org.pottershouse.impactteam.sync.SyncDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class PeerMessageCodecTest {
    @Test
    fun v1MessagesRoundTripAcrossTheWire() {
        val messages = listOf(
            PeerMessage.Hello(DeviceId("device-a")),
            PeerMessage.Digest(
                SyncDigest(
                    highestSequenceByOrigin = mapOf(DeviceId("device-a") to 12L),
                    importantRecordIds = setOf(RecordId("sos-1")),
                ),
            ),
            PeerMessage.Batch(listOf(PeerRecord(locationEnvelope(), relayCount = 2))),
            PeerMessage.Error("INVALID_BATCH", "Batch was rejected"),
        )

        messages.forEach { message ->
            val decoded = PeerMessageCodec.decode(PeerMessageCodec.encode(message))
            assertEquals(message, assertIs<PeerMessageDecodeResult.Success>(decoded).message)
        }
    }

    @Test
    fun payloadLargerThan64KiBIsRejectedBeforeJsonParsing() {
        val oversized = ByteArray(64 * 1024 + 1) { 'x'.code.toByte() }

        val result = PeerMessageCodec.decode(oversized)

        assertIs<PeerMessageDecodeResult.TooLarge>(result)
    }

    @Test
    fun batchWithMoreThan50RecordsCannotBeEncoded() {
        val records = (1..51).map { sequence ->
            PeerRecord(
                locationEnvelope().copy(
                    recordId = RecordId("record-$sequence"),
                    originSequence = sequence.toLong(),
                ),
            )
        }

        assertFailsWith<IllegalArgumentException> {
            PeerMessageCodec.encode(PeerMessage.Batch(records))
        }
    }

    @Test
    fun malformedPayloadIsRejected() {
        val result = PeerMessageCodec.decode("{not-json".encodeToByteArray())

        assertIs<PeerMessageDecodeResult.Invalid>(result)
    }
}
