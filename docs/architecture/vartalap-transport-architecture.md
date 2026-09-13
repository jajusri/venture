# Vartalap Structured Transport Architecture

Status: foundation only; concrete transport remains a specialist gate.

## Purpose

Vartalap is the future transport spine for structured VENTURE business objects. It references canonical objects by type, stable ID, and version. It is not a second source of truth for Orders, Products, Parties, or payment status.

## Delivery path

A sender creates or updates canonical local data, then persists a delivery envelope in the local outbox. A future authenticated transport gateway accepts that envelope and places it in a durable, partitioned store-and-forward queue. Recipient routing delivers it to a durable recipient inbox/event store. Recipient-side reconciliation references the canonical object and records transport evidence separately from commercial state.

The recipient may be offline when the sender submits. Presence or a persistent socket is an optimization, never delivery truth.

## Identity and routing

- Sender business identity is the existing VENTURE company identity (`companyId`).
- Sender device identity must come from a future approved device-identity credential; it is not inferred from an IP address or Connector host.
- Recipient identity is the existing Party/business identity where known. Recipient device and mailbox identifiers are future routing concepts and must be bound by an approved authenticated directory or trust authority.
- Existing Secure Pairing and `TrustedConnectorEndpoint` establish trust for a user's own Connector connection. They do not authenticate a second business or provide cross-business routing.

## Envelope identity

Every envelope has a stable envelope ID and idempotency key, and references one canonical object type, object ID, and version. The envelope records sender, intended recipient, creation time, transport state, retry metadata, and bounded failure information. It does not copy arbitrary Room entities or private pricing data.

## Evidence and state separation

Transport evidence is durable and idempotent:

- `QUEUED`: local delivery intent exists.
- `RELAY_ACCEPTED`: an approved gateway durably accepted the envelope.
- `DELIVERED`: the intended recipient installation/account durably received it.
- `FAILED` / `RETRYING`: delivery did not complete and remains truthfully classified.

Transport states do not imply commercial states. In particular, queued is not Sent, relay acceptance is not Seen, and Delivered is not Confirmed. Commercial Order `DRAFT -> SENT` requires the separately defined durable acceptance evidence; Seen requires a recipient-side open event.

## Retry, duplicates, and ordering

Retries retain the same envelope ID and idempotency key. Consumers suppress duplicate processing by envelope identity. Ordering is scoped to a stable business/recipient mailbox and object stream, not globally; implementations must tolerate partition-local concurrency and out-of-order arrival where the object version makes ordering explicit.

Offline recipients retain envelopes in the relay queue until delivery policy permits retry. Retry attempts must be bounded and backoff policy must be explicit; an unbounded in-memory queue is not authoritative.

## Partitionability and failure isolation

Future gateways should be stateless. Relay storage and workers should partition by stable recipient mailbox/business key, support bounded cursor-based retrieval, and isolate hot or failing partitions. Correctness must not require one global queue scan, one process lock, sticky servers, or globally sequential IDs.

## Revocation and privacy

Future approved trust/credential authority must be able to revoke sender or recipient credentials and prevent new submissions or delivery under revoked authority. Exact revocation propagation, key lifecycle, encryption, and authorization policy are intentionally unspecified here and require specialist security review.

Only the intended recipient may receive an envelope. Hidden/private price data must remain in the canonical privacy boundary and must not be copied into transport metadata or unapproved payloads.

## Specialist gates

This document does not select a relay URL, cloud/vendor, wire protocol, cryptographic scheme, key lifecycle, authentication token, NAT traversal method, or production server. Those decisions are prerequisites for a real transport adapter. Until they are approved and implemented, the production router must report no available transport and leave the envelope `QUEUED`.
