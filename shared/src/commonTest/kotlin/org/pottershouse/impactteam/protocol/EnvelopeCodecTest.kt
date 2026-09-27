package org.pottershouse.impactteam.protocol

import org.pottershouse.impactteam.domain.DeviceId
import org.pottershouse.impactteam.domain.MemberId
import org.pottershouse.impactteam.domain.RecordId
import org.pottershouse.impactteam.domain.TeamId
import org.pottershouse.impactteam.domain.TripId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class EnvelopeCodecTest {
    @Test
    fun encodingMatchesV1GoldenFixture() {
        assertEquals(GOLDEN_JSON, EnvelopeCodec.encode(locationEnvelope()))
    }

    @Test
    fun roundTripPreservesEnvelope() {
        val envelope = locationEnvelope()

        val result = EnvelopeCodec.decode(EnvelopeCodec.encode(envelope))

        assertEquals(envelope, assertIs<DecodeResult.Success>(result).envelope)
    }

    @Test
    fun unknownFieldsAreIgnored() {
        val withUnknownField = GOLDEN_JSON.dropLast(1) + ",\"future_field\":true}"

        val result = EnvelopeCodec.decode(withUnknownField)

        assertEquals(locationEnvelope(), assertIs<DecodeResult.Success>(result).envelope)
    }

    @Test
    fun unsupportedProtocolVersionIsRejected() {
        val versionTwo = GOLDEN_JSON.replace("\"protocol_version\":1", "\"protocol_version\":2")

        val result = EnvelopeCodec.decode(versionTwo)

        assertEquals(2, assertIs<DecodeResult.UnsupportedProtocol>(result).protocolVersion)
    }

    @Test
    fun oversizedInputIsRejectedBeforeParsing() {
        val oversized = " ".repeat(EnvelopeCodec.MAX_ENCODED_BYTES + 1)

        assertIs<DecodeResult.TooLarge>(EnvelopeCodec.decode(oversized))
    }

    private companion object {
        const val GOLDEN_JSON = "{\"protocol_version\":1,\"record_id\":\"record-1\",\"trip_id\":\"trip-1\",\"team_id\":\"blue\",\"member_id\":\"member-1\",\"origin_device_id\":\"device-1\",\"origin_sequence\":7,\"created_at_epoch_millis\":1700000000000,\"priority\":\"NORMAL\",\"expires_at_epoch_millis\":1700003600000,\"payload\":{\"latitude\":-17.8252,\"longitude\":31.0335,\"captured_at_epoch_millis\":1700000000000,\"accuracy_meters\":8.5,\"battery_percent\":81}}"
    }
}

internal fun locationEnvelope() = TrackingEnvelope(
    protocolVersion = 1,
    recordId = RecordId("record-1"),
    tripId = TripId("trip-1"),
    teamId = TeamId("blue"),
    memberId = MemberId("member-1"),
    originDeviceId = DeviceId("device-1"),
    originSequence = 7,
    createdAtEpochMillis = 1_700_000_000_000,
    priority = MessagePriority.NORMAL,
    expiresAtEpochMillis = 1_700_003_600_000,
    payload = LocationPayload(
        latitude = -17.8252,
        longitude = 31.0335,
        capturedAtEpochMillis = 1_700_000_000_000,
        accuracyMeters = 8.5,
        batteryPercent = 81,
    ),
)
