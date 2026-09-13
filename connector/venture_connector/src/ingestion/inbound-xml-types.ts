import type { ParsedXmlDocument } from '../tally/xml/response-parser.js';

/** Contract version for the unified inbound XML envelope boundary. */
export const INBOUND_XML_ENVELOPE_CONTRACT_VERSION = '1' as const;

export const InboundXmlSourceType = {
  InlineBuffer: 'inline_buffer',
  /** Trusted internal caller (tests; future desktop IPC with path capability). No OS file picker exists yet. */
  TrustedInternalFile: 'trusted_internal_file',
  WatchedFolderFile: 'watched_folder_file',
} as const;

export type InboundXmlSourceType =
  (typeof InboundXmlSourceType)[keyof typeof InboundXmlSourceType];

/** Allowlisted inbound master-data resource kinds detected from envelope shape. */
export const InboundXmlResourceKind = {
  Ledgers: 'ledgers',
  StockItems: 'stock-items',
  LedgerGroups: 'ledger-groups',
  CompanyList: 'company-list',
} as const;

export type InboundXmlResourceKind =
  (typeof InboundXmlResourceKind)[keyof typeof InboundXmlResourceKind];

export const InboundXmlValidationStatus = {
  Pending: 'pending',
  Validated: 'validated',
  Rejected: 'rejected',
} as const;

export type InboundXmlValidationStatus =
  (typeof InboundXmlValidationStatus)[keyof typeof InboundXmlValidationStatus];

export const InboundXmlDuplicateStatus = {
  Unique: 'unique',
  Duplicate: 'duplicate',
  InProgress: 'in_progress',
  NotEvaluated: 'not_evaluated',
} as const;

export type InboundXmlDuplicateStatus =
  (typeof InboundXmlDuplicateStatus)[keyof typeof InboundXmlDuplicateStatus];

export const InboundXmlPersistenceStatus = {
  /** Import-attempt history row finalized. Does not imply ledger/stock domain upsert. */
  Completed: 'completed',
  /** Duplicate detected; import-attempt history only. */
  Skipped: 'skipped',
  /** Validation or reservation failed; import-attempt history only. */
  Failed: 'failed',
  /** Reservation held; import-attempt history not yet finalized. */
  NotAttempted: 'not_attempted',
} as const;

export type InboundXmlPersistenceStatus =
  (typeof InboundXmlPersistenceStatus)[keyof typeof InboundXmlPersistenceStatus];

export interface InboundXmlAcceptFileOptions {
  readonly sourceType:
    | typeof InboundXmlSourceType.TrustedInternalFile
    | typeof InboundXmlSourceType.WatchedFolderFile;
  readonly filePath: string;
  /** Required for watched-folder imports. */
  readonly approvedRoot?: string;
  readonly targetCompanyId?: string;
  readonly targetCompanyName?: string;
  readonly expectedResourceKind?: InboundXmlResourceKind;
  readonly recordAttempt?: boolean;
}

export interface InboundXmlAcceptBufferOptions {
  readonly sourceType: InboundXmlSourceType;
  readonly sourceIdentifier?: string;
  readonly targetCompanyId?: string;
  readonly targetCompanyName?: string;
  readonly expectedResourceKind?: InboundXmlResourceKind;
  readonly recordAttempt?: boolean;
}

export type InboundXmlAcceptOptions = InboundXmlAcceptFileOptions | InboundXmlAcceptBufferOptions;

export interface ValidatedInboundXmlEnvelope {
  readonly contractVersion: typeof INBOUND_XML_ENVELOPE_CONTRACT_VERSION;
  readonly sourceType: InboundXmlSourceType;
  readonly sourceIdentifier: string;
  readonly byteSize: number;
  readonly contentFingerprint: string;
  readonly detectedEncoding: 'utf-8';
  readonly resourceKind: InboundXmlResourceKind;
  readonly sourceCompanyName?: string;
  readonly targetCompanyId?: string;
  readonly validationStatus: typeof InboundXmlValidationStatus.Validated;
  readonly duplicateStatus: InboundXmlDuplicateStatus;
  /** Import-attempt/envelope-history outcome only — not domain ledger/stock upsert. */
  readonly persistenceStatus: InboundXmlPersistenceStatus;
  readonly importAttemptId?: string;
  readonly parserVersion: string;
  readonly receivedAt: string;
  readonly rawBytes: Buffer;
  readonly document: ParsedXmlDocument;
  readonly nodeCount: number;
}

export interface InboundXmlRejectResult {
  readonly validationStatus: typeof InboundXmlValidationStatus.Rejected;
  readonly reasonCode: string;
  readonly message: string;
  readonly importAttemptId?: string;
}

export type InboundXmlAcceptResult = ValidatedInboundXmlEnvelope | InboundXmlRejectResult;

export function isValidatedInboundEnvelope(
  result: InboundXmlAcceptResult,
): result is ValidatedInboundXmlEnvelope {
  return result.validationStatus === InboundXmlValidationStatus.Validated;
}

export function isRejectedInboundEnvelope(
  result: InboundXmlAcceptResult,
): result is InboundXmlRejectResult {
  return result.validationStatus === InboundXmlValidationStatus.Rejected;
}
