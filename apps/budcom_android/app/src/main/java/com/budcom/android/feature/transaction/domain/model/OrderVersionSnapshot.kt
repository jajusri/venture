package com.budcom.android.feature.transaction.domain.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import java.math.BigDecimal
import java.security.MessageDigest

data class OrderVersionLineSnapshot(
    val lineId: String,
    val linkedProductId: String?,
    val snapshotProductName: String,
    val snapshotUnit: String?,
    val snapshotSku: String?,
    val quantity: String,
    val unitPriceAmount: String?,
    val unitPriceCurrencyCode: String?,
    val priceState: String,
    val lineTotalAmount: String?,
) {
    init {
        require(lineId.isNotBlank() && lineId.length <= MAX_FIELD)
        require(snapshotProductName.isNotBlank() && snapshotProductName.length <= MAX_FIELD)
        require(linkedProductId == null || linkedProductId.length <= MAX_FIELD)
        require(snapshotUnit == null || snapshotUnit.length <= MAX_FIELD)
        require(snapshotSku == null || snapshotSku.length <= MAX_FIELD)
        require(quantity.length <= MAX_FIELD && requireNotNull(normalizedDecimal(quantity)).toBigDecimal() > BigDecimal.ZERO)
        require(priceState in ALLOWED_PRICE_STATES)
        if (priceState == ACTUAL) {
            require(unitPriceAmount != null && normalizedDecimal(unitPriceAmount) != null)
            require(lineTotalAmount != null && normalizedDecimal(lineTotalAmount) != null)
            require(unitPriceCurrencyCode == null || unitPriceCurrencyCode.length <= MAX_FIELD)
        } else {
            require(unitPriceAmount == null && unitPriceCurrencyCode == null && lineTotalAmount == null)
        }
    }

    companion object {
        const val MAX_FIELD = 128
        const val HIDDEN = "HIDDEN"
        const val CONTACT = "CONTACT_FOR_PRICE"
        const val NO_PRICE = "NO_PRICE_SUPPLIED"
        const val ACTUAL = "ACTUAL"
        val ALLOWED_PRICE_STATES = setOf(HIDDEN, CONTACT, NO_PRICE, ACTUAL)
    }
}

data class OrderVersionSnapshot(
    val contractVersion: Int,
    val orderId: String,
    val orderVersion: Int,
    val senderBusinessId: String,
    val recipientBusinessId: String,
    val createdAtEpochMillis: Long,
    val note: String?,
    val source: String,
    val submissionType: String,
    val envelopeId: String,
    val lines: List<OrderVersionLineSnapshot>,
) {
    init {
        require(contractVersion == CURRENT_CONTRACT_VERSION)
        require(orderId.isNotBlank() && orderId.length <= MAX_FIELD)
        require(orderVersion >= 1)
        require(senderBusinessId.isNotBlank() && senderBusinessId.length <= MAX_FIELD && senderBusinessId != recipientBusinessId)
        require(recipientBusinessId.isNotBlank() && recipientBusinessId.length <= MAX_FIELD)
        require(createdAtEpochMillis >= 0)
        require((note?.length ?: 0) <= MAX_NOTE)
        require(source.isNotBlank() && source.length <= MAX_FIELD)
        require(submissionType.isNotBlank() && submissionType.length <= MAX_FIELD)
        require(envelopeId.isNotBlank() && envelopeId.length <= MAX_FIELD)
        require(lines.isNotEmpty() && lines.size <= MAX_LINES)
        require(lines.map { it.lineId }.toSet().size == lines.size)
        require(deterministicEncoding().toByteArray(Charsets.UTF_8).size <= MAX_PAYLOAD)
    }

    fun deterministicEncoding(): String = buildString {
        append('{')
        append("\"schemaVersion\":").append(contractVersion)
        append(",\"orderId\":").appendJsonString(orderId)
        append(",\"orderVersion\":").append(orderVersion)
        append(",\"senderBusinessId\":").appendJsonString(senderBusinessId)
        append(",\"recipientBusinessId\":").appendJsonString(recipientBusinessId)
        append(",\"createdAtEpochMillis\":").append(createdAtEpochMillis)
        append(",\"note\":").appendJsonNullableString(note)
        append(",\"source\":").appendJsonString(source)
        append(",\"submissionType\":").appendJsonString(submissionType)
        append(",\"envelopeId\":").appendJsonString(envelopeId)
        append(",\"lines\":[")
        lines.sortedBy { it.lineId }.forEachIndexed { index, line ->
            if (index > 0) append(',')
            append('{')
            append("\"lineId\":").appendJsonString(line.lineId)
            append(",\"linkedProductId\":").appendJsonNullableString(line.linkedProductId)
            append(",\"snapshotProductName\":").appendJsonString(line.snapshotProductName)
            append(",\"snapshotUnit\":").appendJsonNullableString(line.snapshotUnit)
            append(",\"snapshotSku\":").appendJsonNullableString(line.snapshotSku)
            append(",\"quantity\":").appendJsonString(requireNotNull(normalizedDecimal(line.quantity)))
            append(",\"unitPriceAmount\":").appendJsonNullableString(line.unitPriceAmount?.let(::normalizedDecimal))
            append(",\"unitPriceCurrencyCode\":").appendJsonNullableString(line.unitPriceCurrencyCode)
            append(",\"priceState\":").appendJsonString(line.priceState)
            append(",\"lineTotalAmount\":").appendJsonNullableString(line.lineTotalAmount?.let(::normalizedDecimal))
            append('}')
        }
        append("]}")
    }

    fun fingerprint(): String = MessageDigest.getInstance("SHA-256")
        .digest(deterministicEncoding().toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }

    fun matchesEnvelope(orderId: String, orderVersion: Int, sender: String, recipient: String): Boolean =
        this.orderId == orderId && this.orderVersion == orderVersion && senderBusinessId == sender && recipientBusinessId == recipient

    companion object {
        const val CURRENT_CONTRACT_VERSION = 2
        const val MAX_FIELD = 256
        const val MAX_NOTE = 512
        const val MAX_LINES = 50
        const val MAX_PAYLOAD = 24_576

        fun fromCanonicalOrder(
            order: CanonicalOrder,
            recipientBusinessId: String,
            envelopeId: String,
        ): OrderVersionSnapshot = OrderVersionSnapshot(
            contractVersion = CURRENT_CONTRACT_VERSION,
            orderId = order.orderId,
            orderVersion = order.version,
            senderBusinessId = order.sellerCompanyId,
            recipientBusinessId = recipientBusinessId,
            createdAtEpochMillis = order.createdAt.epochMillis,
            note = order.note,
            source = order.source.columnValue,
            submissionType = order.submissionType.columnValue,
            envelopeId = envelopeId,
            lines = order.lines.map { it.toSnapshotLine() },
        )

        fun parse(canonical: String): OrderVersionSnapshot? = OrderVersionSnapshotCodec.parse(canonical)
    }
}

object OrderVersionSnapshotCodec {
    fun parse(canonical: String): OrderVersionSnapshot? = runCatching {
        if (canonical.toByteArray(Charsets.UTF_8).size > OrderVersionSnapshot.MAX_PAYLOAD) return null
        val root = Json.parseToJsonElement(canonical) as? JsonObject ?: return null
        if (root.keys != ROOT_KEYS) return null
        val lineElements = root["lines"] as? JsonArray ?: return null
        val snapshot = OrderVersionSnapshot(
            contractVersion = root.requiredInt("schemaVersion") ?: return null,
            orderId = root.requiredString("orderId") ?: return null,
            orderVersion = root.requiredInt("orderVersion") ?: return null,
            senderBusinessId = root.requiredString("senderBusinessId") ?: return null,
            recipientBusinessId = root.requiredString("recipientBusinessId") ?: return null,
            createdAtEpochMillis = root.requiredLong("createdAtEpochMillis") ?: return null,
            note = root.nullableString("note") ?: if (root["note"] === JsonNull) null else return null,
            source = root.requiredString("source") ?: return null,
            submissionType = root.requiredString("submissionType") ?: return null,
            envelopeId = root.requiredString("envelopeId") ?: return null,
            lines = lineElements.map { parseLine(it) ?: return null },
        )
        snapshot.takeIf { it.deterministicEncoding() == canonical }
    }.getOrNull()

    private fun parseLine(element: JsonElement): OrderVersionLineSnapshot? {
        val line = element as? JsonObject ?: return null
        if (line.keys != LINE_KEYS) return null
        val linkedProductId = line.optionalString("linkedProductId") ?: return null
        val snapshotUnit = line.optionalString("snapshotUnit") ?: return null
        val snapshotSku = line.optionalString("snapshotSku") ?: return null
        val unitPriceAmount = line.optionalString("unitPriceAmount") ?: return null
        val unitPriceCurrencyCode = line.optionalString("unitPriceCurrencyCode") ?: return null
        val lineTotalAmount = line.optionalString("lineTotalAmount") ?: return null
        return OrderVersionLineSnapshot(
            lineId = line.requiredString("lineId") ?: return null,
            linkedProductId = linkedProductId.value,
            snapshotProductName = line.requiredString("snapshotProductName") ?: return null,
            snapshotUnit = snapshotUnit.value,
            snapshotSku = snapshotSku.value,
            quantity = line.requiredString("quantity") ?: return null,
            unitPriceAmount = unitPriceAmount.value,
            unitPriceCurrencyCode = unitPriceCurrencyCode.value,
            priceState = line.requiredString("priceState") ?: return null,
            lineTotalAmount = lineTotalAmount.value,
        )
    }

    private val ROOT_KEYS = setOf(
        "schemaVersion", "orderId", "orderVersion", "senderBusinessId", "recipientBusinessId",
        "createdAtEpochMillis", "note", "source", "submissionType", "envelopeId", "lines",
    )
    private val LINE_KEYS = setOf(
        "lineId", "linkedProductId", "snapshotProductName", "snapshotUnit", "snapshotSku", "quantity",
        "unitPriceAmount", "unitPriceCurrencyCode", "priceState", "lineTotalAmount",
    )
}

fun OrderVersionSnapshot.toCanonicalOrder(recipientCompanyId: String, state: CanonicalOrderState): CanonicalOrder =
    CanonicalOrder(
        companyId = recipientCompanyId,
        orderId = orderId,
        creationKey = "received:$orderId",
        sellerCompanyId = senderBusinessId,
        buyerPartyId = recipientBusinessId,
        state = state,
        source = TransactionEntryPointType.fromColumn(source),
        submissionType = TransactionSubmissionType.fromColumn(submissionType),
        note = note,
        createdAt = TransactionTimestamp(createdAtEpochMillis, TransactionTimestampSource.DeviceLocalProvisional),
        version = orderVersion,
        lines = lines.map { line ->
            CanonicalOrderLine(
                orderId = orderId,
                lineId = line.lineId,
                linkedProductId = line.linkedProductId,
                snapshotProductName = line.snapshotProductName,
                snapshotUnit = line.snapshotUnit,
                snapshotSku = line.snapshotSku,
                quantity = line.quantity,
                unitPriceAmount = line.unitPriceAmount,
                unitPriceCurrencyCode = line.unitPriceCurrencyCode,
                priceState = when (line.priceState) {
                    OrderVersionLineSnapshot.ACTUAL -> TransactionDraftPriceState.ActualPrice(
                        requireNotNull(line.unitPriceAmount),
                        line.unitPriceCurrencyCode,
                    )
                    OrderVersionLineSnapshot.NO_PRICE -> TransactionDraftPriceState.NoPriceSupplied
                    OrderVersionLineSnapshot.CONTACT -> TransactionDraftPriceState.ContactForPrice
                    else -> TransactionDraftPriceState.Hidden
                },
                lineTotalAmount = line.lineTotalAmount,
            )
        },
    )

fun OrderVersionSnapshot.detectTamper(expectedFingerprint: String): Boolean = fingerprint() != expectedFingerprint

private fun CanonicalOrderLine.toSnapshotLine(): OrderVersionLineSnapshot {
    val actual = priceState as? TransactionDraftPriceState.ActualPrice
    return OrderVersionLineSnapshot(
        lineId = lineId,
        linkedProductId = linkedProductId,
        snapshotProductName = snapshotProductName,
        snapshotUnit = snapshotUnit,
        snapshotSku = snapshotSku,
        quantity = quantity,
        priceState = when (priceState) {
            is TransactionDraftPriceState.ActualPrice -> OrderVersionLineSnapshot.ACTUAL
            TransactionDraftPriceState.NoPriceSupplied -> OrderVersionLineSnapshot.NO_PRICE
            TransactionDraftPriceState.ContactForPrice -> OrderVersionLineSnapshot.CONTACT
            TransactionDraftPriceState.Hidden -> OrderVersionLineSnapshot.HIDDEN
        },
        unitPriceAmount = actual?.unitAmount,
        unitPriceCurrencyCode = actual?.currencyCode,
        lineTotalAmount = if (actual != null) lineTotalAmount else null,
    )
}

private fun StringBuilder.appendJsonString(value: String): StringBuilder = append(JsonPrimitive(value))
private fun StringBuilder.appendJsonNullableString(value: String?): StringBuilder =
    if (value == null) append("null") else appendJsonString(value)

private fun normalizedDecimal(value: String): String? = runCatching {
    BigDecimal(value).stripTrailingZeros().toPlainString()
}.getOrNull()

private fun JsonObject.requiredString(key: String): String? =
    (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

private fun JsonObject.requiredInt(key: String): Int? =
    (get(key) as? JsonPrimitive)?.takeIf { !it.isString && it.booleanOrNull == null }?.intOrNull

private fun JsonObject.requiredLong(key: String): Long? =
    (get(key) as? JsonPrimitive)?.takeIf { !it.isString && it.booleanOrNull == null }?.longOrNull

private fun JsonObject.nullableString(key: String): String? = when (val value = get(key)) {
    JsonNull -> null
    is JsonPrimitive -> value.takeIf { it.isString }?.contentOrNull
    else -> null
}

private fun JsonObject.optionalString(key: String): OptionalString? = when (val value = get(key)) {
    JsonNull -> OptionalString(null)
    is JsonPrimitive -> value.takeIf { it.isString }?.contentOrNull?.let(::OptionalString)
    else -> null
}

private data class OptionalString(val value: String?)
