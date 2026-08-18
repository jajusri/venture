package com.budcom.android.feature.party.data.local

import com.budcom.android.feature.party.domain.model.FieldProvenanceState
import com.budcom.android.feature.party.domain.model.IssueStatus
import com.budcom.android.feature.party.domain.model.LedgerIdentitySource
import com.budcom.android.feature.party.domain.model.NoteType
import com.budcom.android.feature.party.domain.model.Party
import com.budcom.android.feature.party.domain.model.PartyClassification
import com.budcom.android.feature.party.domain.model.PartyContactPerson
import com.budcom.android.feature.party.domain.model.PartyExportEvent
import com.budcom.android.feature.party.domain.model.PartyFieldProvenance
import com.budcom.android.feature.party.domain.model.PartyIssue
import com.budcom.android.feature.party.domain.model.PartyNote
import com.budcom.android.feature.party.domain.model.PartySourceLink
import com.budcom.android.feature.party.domain.model.PartySourceType
import com.budcom.android.feature.party.domain.model.Tag

internal fun PartyEntity.toDomain(): Party = Party(
    companyId = companyId,
    partyId = partyId,
    displayName = displayName,
    classification = classification.toPartyClassification(),
    primaryPhone = primaryPhone,
    primaryPhoneNormalized = primaryPhoneNormalized,
    primaryEmail = primaryEmail,
    addressLine1 = addressLine1,
    addressCity = addressCity,
    addressState = addressState,
    addressPincode = addressPincode,
    gstin = gstin,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun String.toPartyClassification(): PartyClassification = when (lowercase()) {
    "customer" -> PartyClassification.Customer
    "prospect" -> PartyClassification.Prospect
    "supplier" -> PartyClassification.Supplier
    else -> PartyClassification.Other
}

internal fun String.toFieldProvenanceState(): FieldProvenanceState = when (lowercase()) {
    "confirmed_from_tally" -> FieldProvenanceState.ConfirmedFromTally
    "budcom_only_pending" -> FieldProvenanceState.BudcomOnlyPending
    "export_ready" -> FieldProvenanceState.ExportReady
    "exported" -> FieldProvenanceState.Exported
    "conflict" -> FieldProvenanceState.Conflict
    else -> FieldProvenanceState.EmptyUnknown
}

internal fun FieldProvenanceState.asColumn(): String = when (this) {
    FieldProvenanceState.ConfirmedFromTally -> "confirmed_from_tally"
    FieldProvenanceState.BudcomOnlyPending -> "budcom_only_pending"
    FieldProvenanceState.ExportReady -> "export_ready"
    FieldProvenanceState.Exported -> "exported"
    FieldProvenanceState.Conflict -> "conflict"
    FieldProvenanceState.EmptyUnknown -> "empty_unknown"
}

internal fun String.toIdentitySource(): LedgerIdentitySource = when (lowercase()) {
    "guid" -> LedgerIdentitySource.Guid
    else -> LedgerIdentitySource.Name
}

internal fun LedgerIdentitySource.asColumn(): String = when (this) {
    LedgerIdentitySource.Guid -> "guid"
    LedgerIdentitySource.Name -> "name"
}

internal fun PartySourceLinkEntity.toDomain(): PartySourceLink = PartySourceLink(
    companyId = companyId,
    partyId = partyId,
    sourceType = PartySourceType.TallyLedger,
    sourceInstanceId = sourceInstanceId,
    externalEntityId = externalEntityId,
    externalDisplayName = externalDisplayName,
    identitySource = identitySource.toIdentitySource(),
    lastConfirmedAt = lastConfirmedAt,
)

internal fun PartyFieldProvenanceEntity.toDomain(): PartyFieldProvenance = PartyFieldProvenance(
    companyId = companyId,
    partyId = partyId,
    fieldName = fieldName,
    state = state.toFieldProvenanceState(),
    tallyValue = tallyValue,
    budcomValue = budcomValue,
    lastConfirmedAt = lastConfirmedAt,
    lastExportedAt = lastExportedAt,
    updatedAt = updatedAt,
)

internal fun PartyContactPersonEntity.toDomain(): PartyContactPerson = PartyContactPerson(
    companyId = companyId,
    contactPersonId = contactPersonId,
    partyId = partyId,
    name = name,
    designation = designation,
    mobile = mobile,
    mobileNormalized = mobileNormalized,
    whatsappNumber = whatsappNumber,
    email = email,
    isPrimary = isPrimary,
    provenance = provenance.toFieldProvenanceState(),
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun PartyNoteEntity.toDomain(): PartyNote = PartyNote(
    companyId = companyId,
    noteId = noteId,
    partyId = partyId,
    body = body,
    linkedVoucherId = linkedVoucherId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    type = type.toNoteType(),
    dueAt = dueAt,
    completedAt = completedAt,
    issueId = issueId,
)

internal fun String.toNoteType(): NoteType = when (lowercase()) {
    "payment_issue" -> NoteType.PaymentIssue
    "complaint" -> NoteType.Complaint
    "delivery_issue" -> NoteType.DeliveryIssue
    "commitment" -> NoteType.Commitment
    "product_interest" -> NoteType.ProductInterest
    "internal_remark" -> NoteType.InternalRemark
    "follow_up" -> NoteType.FollowUp
    else -> NoteType.General
}

internal fun NoteType.asColumn(): String = when (this) {
    NoteType.General -> "general"
    NoteType.PaymentIssue -> "payment_issue"
    NoteType.Complaint -> "complaint"
    NoteType.DeliveryIssue -> "delivery_issue"
    NoteType.Commitment -> "commitment"
    NoteType.ProductInterest -> "product_interest"
    NoteType.InternalRemark -> "internal_remark"
    NoteType.FollowUp -> "follow_up"
}

internal fun String.toIssueStatus(): IssueStatus = when (lowercase()) {
    "resolved" -> IssueStatus.Resolved
    else -> IssueStatus.Open
}

internal fun IssueStatus.asColumn(): String = when (this) {
    IssueStatus.Open -> "open"
    IssueStatus.Resolved -> "resolved"
}

internal fun PartyIssueEntity.toDomain(): PartyIssue = PartyIssue(
    companyId = companyId,
    issueId = issueId,
    partyId = partyId,
    title = title,
    status = status.toIssueStatus(),
    createdAt = createdAt,
    resolvedAt = resolvedAt,
    updatedAt = updatedAt,
)

internal fun PartyExportEventEntity.toDomain(): PartyExportEvent = PartyExportEvent(
    companyId = companyId,
    exportId = exportId,
    partyId = partyId,
    createdAt = createdAt,
    outputFileName = outputFileName,
    fieldNames = fieldNamesCsv.split(",").filter { it.isNotBlank() },
)

internal fun TagEntity.toDomain(): Tag = Tag(
    tagId = tagId,
    parentTagId = parentTagId,
    name = name,
    path = path,
    createdAt = createdAt,
)
