package com.budcom.android.feature.party.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * BUDCOM-owned hierarchical/classification tag (e.g. AP -> Rayalaseema -> Chittoor -> Tirupati,
 * or a flat "Dealer"/"Retailer"). Tags themselves are global, reusable definitions, not
 * company-scoped — the same "Dealer" tag concept applies regardless of which Tally company is
 * selected; only its *assignment* to a Party is company-scoped, via [PartyTagCrossRefEntity].
 * Self-referential parent id rather than a comma-delimited path string, per architecture §12.
 */
@Entity(
    tableName = "party_tags",
    indices = [Index(value = ["parentTagId"]), Index(value = ["name"])],
)
data class TagEntity(
    @PrimaryKey val tagId: String,
    val parentTagId: String?,
    val name: String,
    val path: String,
    val createdAt: Long,
)

/** Many-to-many Party<->Tag assignment, scoped by company (transitively, via the Party it points
 * at being company-scoped) exactly like every other Party-adjacent table. */
@Entity(
    tableName = "party_tag_assignments",
    primaryKeys = ["companyId", "partyId", "tagId"],
    indices = [
        Index(value = ["companyId", "tagId"]),
        Index(value = ["tagId"]),
    ],
)
data class PartyTagCrossRefEntity(
    val companyId: String,
    val partyId: String,
    val tagId: String,
    val assignedAt: Long,
)
