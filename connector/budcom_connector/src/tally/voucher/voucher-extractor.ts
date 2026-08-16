import { createHash } from 'node:crypto';

import type {
  VoucherExtractionResult,
  VoucherReadPort,
} from '../../erp/ports/vouchers.js';
import type { VoucherDateRange } from '../../erp/voucher/voucher-domain.js';
import { AppError, ErrorCodes } from '../../infrastructure/errors/app-error.js';
import type { TallyReadGateway } from '../gateway/tally-read-gateway.js';
import { ApprovedOperationId } from '../registry/operation-registry.js';
import { resolveXmlParserOptionsForOperation } from '../xml/response-parser-limits.js';
import {
  TallyXmlResponseParser,
  toPrivacySafeXmlParseDetails,
  XmlParseError,
} from '../xml/response-parser.js';
import { VoucherLedgerEntryParser } from './voucher-ledger-parser.js';
import { joinAndReconcileVoucherLedgers } from './voucher-ledger-reconciler.js';
import { VoucherInventoryEntryParser } from './voucher-inventory-parser.js';
import { joinVoucherInventories } from './voucher-inventory-joiner.js';
import type { VoucherXmlMapper } from './voucher-mapper.js';
import type { VoucherCollectionParser } from './voucher-parser.js';
import { VoucherReconciliationError } from './voucher-reconciliation-error.js';

/** Correlation-only fingerprint of a raw Tally response -- never the content itself. */
function responseHash(rawXml: string): string {
  return createHash('sha256').update(rawXml, 'utf8').digest('hex');
}

export class TallyVoucherExtractor implements VoucherReadPort {
  constructor(
    private readonly gateway: TallyReadGateway,
    private readonly parser: VoucherCollectionParser,
    private readonly mapper: VoucherXmlMapper,
    private readonly ledgerParser = new VoucherLedgerEntryParser(new TallyXmlResponseParser()),
    private readonly enforceTwoPhase = false,
    private readonly inventoryParser = new VoucherInventoryEntryParser(
      new TallyXmlResponseParser(),
    ),
  ) {}

  async readVouchers(
    companyName: string,
    period: VoucherDateRange,
    options: { readonly signal?: AbortSignal } = {},
  ): Promise<VoucherExtractionResult> {
    const exchange = await this.gateway.executeApprovedRead({
      operationId: ApprovedOperationId.Vouchers,
      companyName,
      dateFrom: period.dateFrom,
      dateTo: period.dateTo,
      signal: options.signal,
    });
    const parsed = this.parser.parse(
      exchange.rawXml,
      resolveXmlParserOptionsForOperation(ApprovedOperationId.Vouchers),
    );
    if (parsed.status === 'failure') {
      throw new AppError(
        ErrorCodes.VALIDATION_ERROR,
        parsed.message,
        422,
        {
          reasonCode: parsed.code,
          operation: 'Vouchers',
          responseByteLength: exchange.byteLength,
          responseHash: responseHash(exchange.rawXml),
          ...(parsed.xmlParseDetail ?? {}),
        },
      );
    }
    const illegalCharactersSanitized = parsed.document.illegalCharactersSanitized;
    if (this.enforceTwoPhase && parsed.records.some((node) =>
      ['AMOUNT', 'ALLLEDGERENTRIES.LIST', 'LEDGERENTRIES.LIST', 'ALLINVENTORYENTRIES.LIST']
        .some((name) => this.parser.directChildren(node, name).length > 0))) {
      throw new AppError(
        ErrorCodes.VALIDATION_ERROR,
        'Voucher discovery response contains forbidden monetary or compound fields.',
        422,
        {
          reasonCode: 'voucher-discovery-expansion',
          operation: 'Vouchers',
          responseByteLength: exchange.byteLength,
          responseHash: responseHash(exchange.rawXml),
          illegalCharactersSanitized,
        },
      );
    }
    const mapped = parsed.records.map((node) => this.mapper.map(node));
    let items = mapped.flatMap((result) => result.value ? [result.value] : []);
    const validationIssues = mapped.flatMap((result) => result.issues);
    const droppedRecordCount = mapped.filter((result) => !result.accepted).length;
    const hasFatalValidationIssue = validationIssues.some(
      (issue) => issue.classification === 'rejected' || issue.classification === 'contract-conflict',
    );
    let durationMs = exchange.durationMs;
    let rawByteLength = exchange.byteLength;
    const hasLegacyEmbeddedLedgerRows = !this.enforceTwoPhase &&
      items.some((voucher) => voucher.ledgerEntries.length > 0);
    if (items.length > 0 && !hasLegacyEmbeddedLedgerRows) {
      const ledgerExchange = await this.gateway.executeApprovedRead({
        operationId: ApprovedOperationId.VoucherLedgerEntries,
        companyName,
        dateFrom: period.dateFrom,
        dateTo: period.dateTo,
        signal: options.signal,
      });
      try {
        const ledgerEntries = this.ledgerParser.parse(
          ledgerExchange.rawXml,
          resolveXmlParserOptionsForOperation(ApprovedOperationId.VoucherLedgerEntries),
        );
        items = [...joinAndReconcileVoucherLedgers(items, ledgerEntries)];
      } catch (error) {
        throw new AppError(
          ErrorCodes.VALIDATION_ERROR,
          'Voucher ledger response failed closed validation.',
          422,
          {
            reasonCode: 'voucher-ledger-validation',
            operation: 'VoucherLedgerEntries',
            responseByteLength: ledgerExchange.byteLength,
            responseHash: responseHash(ledgerExchange.rawXml),
            ...(error instanceof XmlParseError ? toPrivacySafeXmlParseDetails(error) : {}),
            ...(error instanceof VoucherReconciliationError
              ? { reconciliationReason: error.reason }
              : {}),
          },
        );
      }
      durationMs += ledgerExchange.durationMs;
      rawByteLength += ledgerExchange.byteLength;

      const inventoryExchange = await this.gateway.executeApprovedRead({
        operationId: ApprovedOperationId.VoucherInventoryEntries,
        companyName,
        dateFrom: period.dateFrom,
        dateTo: period.dateTo,
        signal: options.signal,
      });
      try {
        const inventoryEntries = this.inventoryParser.parse(
          inventoryExchange.rawXml,
          resolveXmlParserOptionsForOperation(ApprovedOperationId.VoucherInventoryEntries),
        );
        items = [...joinVoucherInventories(items, inventoryEntries)];
      } catch (error) {
        throw new AppError(
          ErrorCodes.VALIDATION_ERROR,
          'Voucher inventory response failed closed validation.',
          422,
          {
            reasonCode: 'voucher-inventory-validation',
            operation: 'VoucherInventoryEntries',
            responseByteLength: inventoryExchange.byteLength,
            responseHash: responseHash(inventoryExchange.rawXml),
            ...(error instanceof XmlParseError ? toPrivacySafeXmlParseDetails(error) : {}),
            ...(error instanceof VoucherReconciliationError
              ? { reconciliationReason: error.reason }
              : {}),
          },
        );
      }
      durationMs += inventoryExchange.durationMs;
      rawByteLength += inventoryExchange.byteLength;
    }
    return {
      items,
      candidateRecordCount: parsed.records.length,
      droppedRecordCount,
      validationIssues,
      responseStatus:
        parsed.status === 'partial' || droppedRecordCount > 0 || hasFatalValidationIssue
          ? 'partial'
          : parsed.status,
      durationMs,
      rawByteLength,
      illegalCharactersSanitized,
    };
  }
}
