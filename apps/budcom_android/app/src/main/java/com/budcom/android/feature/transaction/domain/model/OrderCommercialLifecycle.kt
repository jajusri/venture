package com.budcom.android.feature.transaction.domain.model

/** Plain-language buyer-facing labels — no relay or protocol jargon. */
object CanonicalOrderStatusLabels {
    fun buyerFacing(state: CanonicalOrderState): String = when (state) {
        CanonicalOrderState.Draft -> "Draft"
        CanonicalOrderState.Sent -> "Sent"
        CanonicalOrderState.Seen -> "Seen"
        CanonicalOrderState.Confirmed -> "Confirmed"
        CanonicalOrderState.RevisionPending -> "Revision in progress"
        CanonicalOrderState.RevisionSent -> "Revision sent"
        CanonicalOrderState.RevisionSeen -> "Revision seen"
    }
}

data class OrderConfirmAuthority(
    val businessId: String,
    val actorId: String,
    val deviceId: String,
    val authorityScope: Set<String>,
    val authorityEpoch: Long,
) {
    init {
        require(businessId.isNotBlank())
        require(actorId.isNotBlank())
        require(deviceId.isNotBlank())
        require(authorityEpoch >= 0)
    }

    fun permitsOrderConfirm(): Boolean = authorityScope.contains(CONFIRM_ORDERS_CAPABILITY)

    fun permitsOrderRevision(): Boolean = authorityScope.contains(REVISE_ORDERS_CAPABILITY)

    fun permitsRevisionAccept(): Boolean = authorityScope.contains(ACCEPT_ORDER_REVISIONS_CAPABILITY)

    fun scopeFingerprint(): String = authorityScope.sorted().joinToString(",")

    companion object {
        const val CONFIRM_ORDERS_CAPABILITY = "confirm_orders"
        const val REVISE_ORDERS_CAPABILITY = "revise_orders"
        const val ACCEPT_ORDER_REVISIONS_CAPABILITY = "accept_order_revisions"
    }
}

data class OrderConfirmEvidence(
    val eventId: String,
    val orderId: String,
    val orderVersion: Int,
    val confirmingBusinessId: String,
    val confirmingActorId: String,
    val confirmingDeviceId: String,
    val senderBusinessId: String,
    val authorityEpoch: Long,
    val authorityScopeFingerprint: String,
    val confirmedAt: TransactionTimestamp,
)

data class OrderRevisionAcceptEvidence(
    val eventId: String,
    val orderId: String,
    val orderVersion: Int,
    val acceptingBusinessId: String,
    val acceptingActorId: String,
    val acceptingDeviceId: String,
    val counterpartyBusinessId: String,
    val authorityEpoch: Long,
    val acceptedAt: TransactionTimestamp,
)

data class OrderRevisionLineChange(
    val lineId: String,
    val linkedProductId: String?,
    val snapshotProductName: String,
    val snapshotUnit: String?,
    val snapshotSku: String?,
    val quantity: String,
    val unitPriceAmount: String?,
    val unitPriceCurrencyCode: String?,
    val priceState: TransactionDraftPriceState,
    val lineTotalAmount: String?,
)

enum class OrderMaterialField {
    Product,
    Quantity,
    UnitPrice,
    PriceTier,
    CommercialTerm,
    DeliveryLocation,
}

data class OrderMaterialChangeReport(
    val changedFields: Set<OrderMaterialField>,
) {
    val isMaterial: Boolean get() = changedFields.isNotEmpty()
}

object OrderMaterialChangeClassifier {
    fun classify(baseline: CanonicalOrder, proposedLines: List<OrderRevisionLineChange>, proposedNote: String?): OrderMaterialChangeReport {
        val changed = mutableSetOf<OrderMaterialField>()
        val baselineByLine = baseline.lines.associateBy { it.lineId }
        val proposedByLine = proposedLines.associateBy { it.lineId }
        if (baselineByLine.keys != proposedByLine.keys) changed += OrderMaterialField.Product
        for ((lineId, baseLine) in baselineByLine) {
            val proposed = proposedByLine[lineId] ?: continue
            if (baseLine.linkedProductId != proposed.linkedProductId || baseLine.snapshotProductName != proposed.snapshotProductName) {
                changed += OrderMaterialField.Product
            }
            if (baseLine.snapshotSku != proposed.snapshotSku) changed += OrderMaterialField.Product
            if (baseLine.quantity != proposed.quantity) changed += OrderMaterialField.Quantity
            if (baseLine.unitPriceAmount != proposed.unitPriceAmount || baseLine.unitPriceCurrencyCode != proposed.unitPriceCurrencyCode) {
                changed += OrderMaterialField.UnitPrice
            }
            if (baseLine.priceState.toColumnValue() != proposed.priceState.toColumnValue()) changed += OrderMaterialField.PriceTier
        }
        val normalizedBaselineNote = baseline.note?.trim().orEmpty()
        val normalizedProposedNote = proposedNote?.trim().orEmpty()
        if (normalizedBaselineNote != normalizedProposedNote && normalizedProposedNote.isNotEmpty()) {
            changed += OrderMaterialField.CommercialTerm
        }
        return OrderMaterialChangeReport(changed)
    }
}

object OrderConfirmAuthorityValidation {
    fun validateSellerConfirm(
        order: CanonicalOrder,
        inbox: StructuredRecipientInboxEntry,
        authority: OrderConfirmAuthority,
        viewerCompanyId: String,
    ): Boolean {
        if (!authority.permitsOrderConfirm()) return false
        if (authority.businessId != viewerCompanyId || inbox.companyId != viewerCompanyId) return false
        if (inbox.objectId != order.orderId || inbox.objectVersion != order.version) return false
        if (inbox.senderBusinessId == viewerCompanyId) return false
        if (order.buyerPartyId != null && order.buyerPartyId != inbox.senderBusinessId) return false
        return order.state == CanonicalOrderState.Seen || order.state == CanonicalOrderState.RevisionSeen
    }
}

object CanonicalOrderConfirmedTransitions {
    fun apply(order: CanonicalOrder, evidence: OrderConfirmEvidence): CanonicalOrderState? {
        if (evidence.orderId != order.orderId || evidence.orderVersion != order.version) return null
        if (evidence.confirmingBusinessId == order.companyId) return null
        if (evidence.senderBusinessId != order.companyId) return null
        return when (order.state) {
            CanonicalOrderState.Confirmed -> CanonicalOrderState.Confirmed
            CanonicalOrderState.Sent, CanonicalOrderState.Seen -> CanonicalOrderState.Confirmed
            CanonicalOrderState.RevisionSeen -> CanonicalOrderState.Confirmed
            else -> null
        }
    }
}

object CanonicalOrderRevisionAcceptTransitions {
    fun apply(order: CanonicalOrder, evidence: OrderRevisionAcceptEvidence): CanonicalOrderState? {
        if (evidence.orderId != order.orderId || evidence.orderVersion != order.version) return null
        if (evidence.acceptingBusinessId != order.companyId) return null
        if (evidence.counterpartyBusinessId == order.companyId) return null
        return when (order.state) {
            CanonicalOrderState.Confirmed -> CanonicalOrderState.Confirmed
            CanonicalOrderState.RevisionSent, CanonicalOrderState.RevisionSeen -> CanonicalOrderState.Confirmed
            else -> null
        }
    }
}

object CanonicalOrderLifecycleIntegrity {
    fun isLegalTransition(from: CanonicalOrderState, to: CanonicalOrderState): Boolean = when (from) {
        CanonicalOrderState.Draft -> to == CanonicalOrderState.Sent
        CanonicalOrderState.Sent -> to == CanonicalOrderState.Seen || to == CanonicalOrderState.Confirmed
        CanonicalOrderState.Seen -> to == CanonicalOrderState.Confirmed || to == CanonicalOrderState.RevisionPending
        CanonicalOrderState.RevisionPending -> to == CanonicalOrderState.RevisionSent
        CanonicalOrderState.RevisionSent -> to == CanonicalOrderState.RevisionSeen
        CanonicalOrderState.RevisionSeen -> to == CanonicalOrderState.Confirmed || to == CanonicalOrderState.RevisionPending
        CanonicalOrderState.Confirmed -> to == CanonicalOrderState.Confirmed
    }

    fun rejectsIllegalDirectTransition(from: CanonicalOrderState, to: CanonicalOrderState): Boolean = !isLegalTransition(from, to)
}

private fun TransactionDraftPriceState.toColumnValue(): String = when (this) {
    is TransactionDraftPriceState.ActualPrice -> "ACTUAL"
    TransactionDraftPriceState.NoPriceSupplied -> "NO_PRICE_SUPPLIED"
    TransactionDraftPriceState.ContactForPrice -> "CONTACT_FOR_PRICE"
    TransactionDraftPriceState.Hidden -> "HIDDEN"
}
