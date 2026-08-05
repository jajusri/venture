package com.budcom.android.core.pairing.domain.model

import com.budcom.android.core.util.TimeProvider
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.Base64
import javax.inject.Inject

/** Every reason [SecurePairingQrPayloadParser.parse] can reject a payload. */
sealed class SecurePairingQrPayloadRejection {
    data object PayloadTooLarge : SecurePairingQrPayloadRejection()
    data object MalformedJson : SecurePairingQrPayloadRejection()
    data class MissingFields(val fields: List<String>) : SecurePairingQrPayloadRejection()
    data class DuplicateFields(val fields: List<String>) : SecurePairingQrPayloadRejection()
    data class UnsupportedSchema(val schemaVersion: String) : SecurePairingQrPayloadRejection()
    data object UnsupportedTransportProtocol : SecurePairingQrPayloadRejection()
    data object UnsupportedFingerprintAlgorithm : SecurePairingQrPayloadRejection()
    data object MalformedFingerprint : SecurePairingQrPayloadRejection()
    data object InvalidFingerprintLength : SecurePairingQrPayloadRejection()
    data object InvalidPort : SecurePairingQrPayloadRejection()
    data class InvalidHost(val reason: String) : SecurePairingQrPayloadRejection()
    data object MalformedExpiry : SecurePairingQrPayloadRejection()
    data object ExpiryNotInFuture : SecurePairingQrPayloadRejection()
    data object ExpiryTooFarInFuture : SecurePairingQrPayloadRejection()
}

sealed class SecurePairingQrPayloadParseResult {
    data class Valid(val payload: SecurePairingQrPayload) : SecurePairingQrPayloadParseResult()
    data class Invalid(val rejection: SecurePairingQrPayloadRejection) : SecurePairingQrPayloadParseResult()
}

/**
 * Explicit, injectable test/emulator hook — never a release-build compile-time bypass. Defaults
 * to the physical-device policy (loopback rejected). Only [allowLoopbackHost] varies; every other
 * rule (wildcard/public-internet rejection, fingerprint/expiry/port checks) is unconditional.
 */
data class SecurePairingQrValidationPolicy(
    val allowLoopbackHost: Boolean = false,
)

/**
 * Strict, bounded parser/validator for the Desktop-generated secure-pairing QR payload.
 *
 * Deliberately does not decode via a `@Serializable` data class + [Json.decodeFromString]: this
 * payload is a security boundary (it carries a one-time secret and the value the whole pinned-TLS
 * trust model will anchor on), so field extraction and duplicate-key detection are done by hand
 * against the parsed [JsonObject] tree rather than relying on a generated serializer's default
 * handling of unknown/duplicate keys.
 */
class SecurePairingQrPayloadParser @Inject constructor(
    private val timeProvider: TimeProvider,
) {
    private val json = Json { isLenient = false }

    fun parse(
        raw: String,
        policy: SecurePairingQrValidationPolicy = SecurePairingQrValidationPolicy(),
    ): SecurePairingQrPayloadParseResult {
        if (raw.toByteArray(Charsets.UTF_8).size > MAX_PAYLOAD_BYTES) {
            return invalid(SecurePairingQrPayloadRejection.PayloadTooLarge)
        }

        val duplicates = findDuplicateTopLevelKeys(raw)
        if (duplicates.isNotEmpty()) {
            return invalid(SecurePairingQrPayloadRejection.DuplicateFields(duplicates))
        }

        val obj = try {
            json.parseToJsonElement(raw) as? JsonObject ?: return invalid(SecurePairingQrPayloadRejection.MalformedJson)
        } catch (e: SerializationException) {
            return invalid(SecurePairingQrPayloadRejection.MalformedJson)
        } catch (e: IllegalArgumentException) {
            return invalid(SecurePairingQrPayloadRejection.MalformedJson)
        }

        val missing = mutableListOf<String>()
        val schemaVersion = obj.stringField("schemaVersion", missing)
        val pairingSessionId = obj.stringField("pairingSessionId", missing)
        val secret = obj.stringField("secret", missing)
        val connectorId = obj.stringField("connectorId", missing)
        val connectorName = obj.stringField("connectorName", missing)
        val host = obj.stringField("host", missing)
        val port = obj.intField("port", missing)
        val securePort = obj.intField("securePort", missing)
        val transportProtocol = obj.stringField("transportProtocol", missing)
        val transportFingerprint = obj.stringField("transportFingerprint", missing)
        val fingerprintAlgorithm = obj.stringField("fingerprintAlgorithm", missing)
        val transportIdentityVersion = obj.intField("transportIdentityVersion", missing)
        val expiresAt = obj.stringField("expiresAt", missing)

        if (missing.isNotEmpty()) {
            return invalid(SecurePairingQrPayloadRejection.MissingFields(missing))
        }
        // Every value below is non-null: `missing` would already have short-circuited otherwise.
        requireNotNull(schemaVersion); requireNotNull(pairingSessionId); requireNotNull(secret)
        requireNotNull(connectorId); requireNotNull(connectorName); requireNotNull(host)
        requireNotNull(port); requireNotNull(securePort); requireNotNull(transportProtocol)
        requireNotNull(transportFingerprint); requireNotNull(fingerprintAlgorithm)
        requireNotNull(transportIdentityVersion); requireNotNull(expiresAt)

        if (schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            return invalid(SecurePairingQrPayloadRejection.UnsupportedSchema(schemaVersion))
        }
        if (transportProtocol != "https") {
            return invalid(SecurePairingQrPayloadRejection.UnsupportedTransportProtocol)
        }
        if (port !in 1..65535 || securePort !in 1..65535) {
            return invalid(SecurePairingQrPayloadRejection.InvalidPort)
        }

        val fingerprintRejection = validateFingerprint(fingerprintAlgorithm, transportFingerprint)
        if (fingerprintRejection != null) return invalid(fingerprintRejection)

        val hostRejection = validateHost(host, policy)
        if (hostRejection != null) return invalid(hostRejection)

        val expiresAtEpochMillis = try {
            Instant.parse(expiresAt).toEpochMilli()
        } catch (e: DateTimeParseException) {
            return invalid(SecurePairingQrPayloadRejection.MalformedExpiry)
        }
        val now = timeProvider.nowEpochMillis()
        if (expiresAtEpochMillis <= now) {
            return invalid(SecurePairingQrPayloadRejection.ExpiryNotInFuture)
        }
        if (expiresAtEpochMillis - now > MAX_REASONABLE_TTL_MS) {
            return invalid(SecurePairingQrPayloadRejection.ExpiryTooFarInFuture)
        }

        return SecurePairingQrPayloadParseResult.Valid(
            SecurePairingQrPayload(
                schemaVersion = schemaVersion,
                pairingSessionId = pairingSessionId,
                secret = secret,
                connectorId = connectorId,
                connectorName = connectorName,
                host = host,
                port = port,
                securePort = securePort,
                transportProtocol = transportProtocol,
                transportFingerprint = transportFingerprint,
                fingerprintAlgorithm = fingerprintAlgorithm,
                transportIdentityVersion = transportIdentityVersion,
                expiresAtEpochMillis = expiresAtEpochMillis,
            ),
        )
    }

    private fun invalid(rejection: SecurePairingQrPayloadRejection) = SecurePairingQrPayloadParseResult.Invalid(rejection)

    private companion object {
        /** Well below any real QR-code capacity limit; defense against a non-QR paste/deep-link vector. */
        const val MAX_PAYLOAD_BYTES = 4_096
        const val SUPPORTED_SCHEMA_VERSION = "1"

        /**
         * The Connector bounds a pairing session's own TTL to 300_000ms (see
         * `PAIRING_SESSION_REPOSITORY.MAX_TTL_MS` server-side) — this allows a modest clock-skew
         * margin beyond that server-side maximum rather than matching it exactly.
         */
        const val MAX_REASONABLE_TTL_MS = 330_000L
    }
}

private fun validateFingerprint(fingerprintAlgorithm: String, transportFingerprint: String): SecurePairingQrPayloadRejection? {
    if (fingerprintAlgorithm != "sha256") return SecurePairingQrPayloadRejection.UnsupportedFingerprintAlgorithm
    if (!transportFingerprint.startsWith("sha256/")) return SecurePairingQrPayloadRejection.MalformedFingerprint
    val encoded = transportFingerprint.removePrefix("sha256/")
    val decoded = try {
        Base64.getDecoder().decode(encoded)
    } catch (e: IllegalArgumentException) {
        return SecurePairingQrPayloadRejection.MalformedFingerprint
    }
    return if (decoded.size != 32) SecurePairingQrPayloadRejection.InvalidFingerprintLength else null
}

private val IPV4_PATTERN = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""")
private val IPV6_LITERAL_CHARSET = Regex("""^[0-9A-Fa-f:]+$""")

private enum class HostClassification { WILDCARD, LOOPBACK, PRIVATE, PUBLIC }

/**
 * Host must be a bare IPv4/IPv6 literal — no scheme, path, query, fragment, userinfo, or
 * hostname. Restricting to literals also satisfies "no automatic DNS lookup merely while
 * parsing": there is never a hostname for [InetAddress.getByName] to resolve over the network —
 * for a syntactically numeric literal it only parses bytes locally, per the JDK's own contract.
 */
private fun validateHost(host: String, policy: SecurePairingQrValidationPolicy): SecurePairingQrPayloadRejection? {
    if (host.isEmpty() || host.any { it.code < 0x21 || it.code == 0x7F } || host.any { it in "/?#@\\" } || host.contains("://")) {
        return SecurePairingQrPayloadRejection.InvalidHost("contains disallowed characters")
    }

    val classification = classifyIpv4(host) ?: classifyIpv6(host)
        ?: return SecurePairingQrPayloadRejection.InvalidHost("not a recognized IPv4/IPv6 literal")

    return when (classification) {
        HostClassification.WILDCARD -> SecurePairingQrPayloadRejection.InvalidHost("wildcard address not permitted")
        HostClassification.LOOPBACK ->
            if (policy.allowLoopbackHost) null else SecurePairingQrPayloadRejection.InvalidHost("loopback address not permitted")
        HostClassification.PUBLIC -> SecurePairingQrPayloadRejection.InvalidHost("public internet address not permitted")
        HostClassification.PRIVATE -> null
    }
}

private fun classifyIpv4(host: String): HostClassification? {
    val match = IPV4_PATTERN.matchEntire(host) ?: return null
    val groups = match.groupValues.drop(1)
    if (groups.any { it.length > 1 && it.startsWith("0") }) return null // reject leading-zero octets (e.g. "010")
    val octets = groups.map { it.toIntOrNull() ?: return null }
    if (octets.any { it !in 0..255 }) return null
    val (a, b, c, d) = octets
    return when {
        a == 0 && b == 0 && c == 0 && d == 0 -> HostClassification.WILDCARD
        a == 127 -> HostClassification.LOOPBACK
        a == 10 -> HostClassification.PRIVATE
        a == 172 && b in 16..31 -> HostClassification.PRIVATE
        a == 192 && b == 168 -> HostClassification.PRIVATE
        else -> HostClassification.PUBLIC
    }
}

private fun classifyIpv6(host: String): HostClassification? {
    if (!host.contains(':') || !IPV6_LITERAL_CHARSET.matches(host)) return null
    val address = try {
        InetAddress.getByName(host) as? Inet6Address ?: return null
    } catch (e: UnknownHostException) {
        return null
    }
    val firstByte = address.address[0].toInt() and 0xFF
    return when {
        address.isAnyLocalAddress -> HostClassification.WILDCARD
        address.isLoopbackAddress -> HostClassification.LOOPBACK
        address.isLinkLocalAddress -> HostClassification.PRIVATE
        address.isSiteLocalAddress -> HostClassification.PRIVATE
        (firstByte and 0xFE) == 0xFC -> HostClassification.PRIVATE // fc00::/7 (ULA)
        else -> HostClassification.PUBLIC
    }
}

private fun JsonObject.stringField(name: String, missing: MutableList<String>): String? {
    val primitive = this[name] as? JsonPrimitive
    if (primitive == null || !primitive.isString) {
        missing += name
        return null
    }
    return primitive.content
}

private fun JsonObject.intField(name: String, missing: MutableList<String>): Int? {
    val primitive = this[name] as? JsonPrimitive
    val value = if (primitive == null || primitive.isString) null else primitive.content.toIntOrNull()
    if (value == null) {
        missing += name
        return null
    }
    return value
}

/**
 * Scans only for duplicate KEYS at the payload's top level, independent of whatever duplicate-key
 * behavior the JSON library applies internally. String VALUES are read and skipped wholesale
 * (respecting `\"` escaping) so a legitimate value that happens to contain key-shaped text (e.g.
 * a secret containing `"connectorId":`) can never be misdetected as a duplicate key.
 */
private fun findDuplicateTopLevelKeys(raw: String): List<String> {
    val counts = mutableMapOf<String, Int>()
    var i = 0
    var depth = 0
    var expectingKey = false

    fun readStringLiteral(): String? {
        if (i >= raw.length || raw[i] != '"') return null
        val sb = StringBuilder()
        i++
        while (i < raw.length) {
            val c = raw[i]
            when {
                c == '\\' && i + 1 < raw.length -> { sb.append(c).append(raw[i + 1]); i += 2 }
                c == '"' -> { i++; return sb.toString() }
                else -> { sb.append(c); i++ }
            }
        }
        return null
    }

    while (i < raw.length) {
        val c = raw[i]
        if (c.isWhitespace()) { i++; continue }
        when (c) {
            '{' -> { depth++; expectingKey = depth == 1; i++ }
            '}' -> { depth--; expectingKey = false; i++ }
            '[' -> { depth++; expectingKey = false; i++ }
            ']' -> { depth--; expectingKey = false; i++ }
            ',' -> { expectingKey = depth == 1; i++ }
            '"' -> {
                if (expectingKey && depth == 1) {
                    val key = readStringLiteral()
                    if (key != null) counts[key] = (counts[key] ?: 0) + 1
                    expectingKey = false
                } else {
                    readStringLiteral()
                }
            }
            else -> i++
        }
    }
    return counts.filterValues { it > 1 }.keys.toList()
}
