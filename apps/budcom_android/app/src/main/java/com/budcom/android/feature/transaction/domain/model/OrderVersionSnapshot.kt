package com.budcom.android.feature.transaction.domain.model

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
        require(quantity.isNotBlank() && quantity.length <= MAX_FIELD)
        require(priceState in ALLOWED_PRICE_STATES)
        if (priceState == HIDDEN || priceState == CONTACT || priceState == NO_PRICE) {
            require(unitPriceAmount == null && lineTotalAmount == null)
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
        require(senderBusinessId.isNotBlank() && senderBusinessId != recipientBusinessId)
        require(recipientBusinessId.isNotBlank())
        require((note?.length ?: 0) <= MAX_NOTE)
        require(source.isNotBlank() && submissionType.isNotBlank())
        require(envelopeId.isNotBlank() && envelopeId.length <= MAX_FIELD)
        require(lines.isNotEmpty() && lines.size <= MAX_LINES)
        require(deterministicEncoding().length <= MAX_PAYLOAD)
    }

    fun deterministicEncoding(): String = buildString {
        append("v=").append(contractVersion)
        append("|order=").append(esc(orderId))
        append("|version=").append(orderVersion)
        append("|sender=").append(esc(senderBusinessId))
        append("|recipient=").append(esc(recipientBusinessId))
        append("|created=").append(createdAtEpochMillis)
        append("|note=").append(esc(note.orEmpty()))
        append("|source=").append(esc(source))
        append("|submission=").append(esc(submissionType))
        append("|envelope=").append(esc(envelopeId))
        lines.sortedBy { it.lineId }.forEach { line ->
            append("|line.").append(esc(line.lineId))
            append("=product:").append(esc(line.linkedProductId.orEmpty()))
            append(";name:").append(esc(line.snapshotProductName))
            append(";unit:").append(esc(line.snapshotUnit.orEmpty()))
            append(";sku:").append(esc(line.snapshotSku.orEmpty()))
            append(";qty:").append(esc(line.quantity))
            append(";priceState:").append(esc(line.priceState))
            append(";unitPrice:").append(esc(line.unitPriceAmount.orEmpty()))
            append(";currency:").append(esc(line.unitPriceCurrencyCode.orEmpty()))
            append(";total:").append(esc(line.lineTotalAmount.orEmpty()))
        }
    }

    fun fingerprint(): String = MessageDigest.getInstance("SHA-256")
        .digest(deterministicEncoding().toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }

    fun matchesEnvelope(orderId: String, orderVersion: Int, sender: String, recipient: String): Boolean =
        this.orderId == orderId && this.orderVersion == orderVersion && senderBusinessId == sender && recipientBusinessId == recipient

    companion object {
        const val CURRENT_CONTRACT_VERSION = 1
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
        if (canonical.length > OrderVersionSnapshot.MAX_PAYLOAD) return null
        val fields = linkedMapOf<String, String>()
        val lines = mutableListOf<OrderVersionLineSnapshot>()
        canonical.split('|').forEach { part ->
            val eq = part.indexOf('=')
            if (eq <= 0) return@forEach
            val key = unesc(part.substring(0, eq))
            val value = unesc(part.substring(eq + 1))
            if (key.startsWith("line.")) {
                lines += parseLine(key.removePrefix("line."), value) ?: return null
            } else {
                fields[key] = value
            }
        }
        OrderVersionSnapshot(
            contractVersion = fields["v"]?.toInt() ?: return null,
            orderId = fields["order"] ?: return null,
            orderVersion = fields["version"]?.toInt() ?: return null,
            senderBusinessId = fields["sender"] ?: return null,
            recipientBusinessId = fields["recipient"] ?: return null,
            createdAtEpochMillis = fields["created"]?.toLong() ?: return null,
            note = fields["note"]?.takeIf { it.isNotEmpty() },
            source = fields["source"] ?: return null,
            submissionType = fields["submission"] ?: return null,
            envelopeId = fields["envelope"] ?: return null,
            lines = lines,
        )
    }.getOrNull()

    private fun parseLine(lineId: String, value: String): OrderVersionLineSnapshot? {
        val parts = value.split(';').associate { token ->
            val idx = token.indexOf(':')
            if (idx <= 0) return null
            token.substring(0, idx) to token.substring(idx + 1)
        }
        return OrderVersionLineSnapshot(
            lineId = lineId,
            linkedProductId = parts["product"]?.takeIf { it.isNotEmpty() },
            snapshotProductName = parts["name"] ?: return null,
            snapshotUnit = parts["unit"]?.takeIf { it.isNotEmpty() },
            snapshotSku = parts["sku"]?.takeIf { it.isNotEmpty() },
            quantity = parts["qty"] ?: return null,
            priceState = parts["priceState"] ?: return null,
            unitPriceAmount = parts["unitPrice"]?.takeIf { it.isNotEmpty() },
            unitPriceCurrencyCode = parts["currency"]?.takeIf { it.isNotEmpty() },
            lineTotalAmount = parts["total"]?.takeIf { it.isNotEmpty() },
        )
    }
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
    val hidden = priceState is TransactionDraftPriceState.Hidden
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
        unitPriceAmount = if (hidden) null else unitPriceAmount,
        unitPriceCurrencyCode = if (hidden) null else unitPriceCurrencyCode,
        lineTotalAmount = if (hidden) null else lineTotalAmount,
    )
}

private fun esc(value: String): String = value.replace("\\", "\\\\").replace("|", "\\|").replace("=", "\\=").replace(";", "\\;")
private fun unesc(value: String): String = buildString {
    var i = 0
    while (i < value.length) {
        if (value[i] == '\\' && i + 1 < value.length) {
            append(value[i + 1])
            i += 2
        } else {
            append(value[i])
            i += 1
        }
    }
}
