/**
 * Import-attempt reservation lifecycle (Reliability order item 6).
 *
 * Status model — reservation_status drives duplicate locking:
 * - active:     reservation held; validation/import in progress
 * - completed:  successful unique import recorded; duplicate-protected
 * - released:   validation failed or reservation cancelled; retry allowed
 * - abandoned:  active reservation recovered after process restart
 *
 * validation_status:
 * - pending:   reserved, validation not finished
 * - validated: envelope accepted
 * - rejected:  envelope rejected or reservation abandoned/released
 *
 * persistence_status (import-attempt history only — no domain ledger/stock upsert in this gate):
 * - not_attempted: reserved only
 * - completed:     unique import-attempt history row committed
 * - skipped:         duplicate detected
 * - failed:          rejected or abandoned
 */

export const InboundXmlReservationStatus = {
  Active: 'active',
  Completed: 'completed',
  Released: 'released',
  Abandoned: 'abandoned',
} as const;

export type InboundXmlReservationStatus =
  (typeof InboundXmlReservationStatus)[keyof typeof InboundXmlReservationStatus];

/** Minimum age before an active reservation may be recovered on startup. */
export const XML_IMPORT_ACTIVE_RESERVATION_GRACE_MS = 30_000;

export type XmlImportReservationOutcomeKind = 'acquired' | 'duplicate' | 'in_progress';

export interface XmlImportReservationOutcome {
  readonly kind: XmlImportReservationOutcomeKind;
  readonly importAttemptId: string;
}
