package com.budcom.android.feature.party.data.local

import com.budcom.android.feature.party.domain.model.FieldProvenanceState
import com.budcom.android.feature.party.domain.model.LedgerIdentitySource
import com.budcom.android.feature.party.domain.model.Party
import com.budcom.android.feature.party.domain.model.PartyClassification
import com.budcom.android.feature.party.domain.model.PartyContactPerson
import com.budcom.android.feature.party.domain.model.PartyFieldProvenance
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
)

internal fun TagEntity.toDomain(): Tag = Tag(
    tagId = tagId,
    parentTagId = parentTagId,
    name = name,
    path = path,
    createdAt = createdAt,
)
