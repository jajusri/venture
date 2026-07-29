import { createHash } from 'node:crypto';

import { TallyXmlRequestBuilder } from '../xml/request-builder.js';
import { validateTallyRequestXml } from '../safety/xml-request-validator.js';
import {
  buildVoucherCollectionRequestSpec,
  PRODUCTION_VOUCHER_FETCH_METHODS,
} from '../voucher/voucher-request.js';
import {
  VOUCHER_DISCOVERY_OPERATION,
  VOUCHER_DISCOVERY_OPERATION_ID,
  type VoucherDiscoveryRequestShape,
} from '../registry/voucher-discovery-operation.js';

export const VOUCHER_DISCOVERY_MAX_REQUEST_BYTES = 65_536;
export const VOUCHER_DISCOVERY_FETCH_METHODS = PRODUCTION_VOUCHER_FETCH_METHODS;

export interface BuiltVoucherDiscoveryRequest {
  readonly operationId: typeof VOUCHER_DISCOVERY_OPERATION_ID;
  readonly xml: string;
  readonly companyToken: string;
  readonly dateFrom: string;
  readonly dateTo: string;
}

export function fixtureCompanyToken(companyName: string): string {
  return `fixture-${createHash('sha256').update(companyName, 'utf8').digest('hex').slice(0, 16)}`;
}

export function buildVoucherDiscoveryRequest(
  request: VoucherDiscoveryRequestShape,
): BuiltVoucherDiscoveryRequest {
  if (request.operationId !== VOUCHER_DISCOVERY_OPERATION_ID) {
    throw new Error('Only the approved Voucher discovery candidate is permitted.');
  }
  if (
    VOUCHER_DISCOVERY_OPERATION.classification !== 'EXPERIMENTAL_DISABLED' ||
    VOUCHER_DISCOVERY_OPERATION.rolloutStatus !== 'disabled' ||
    VOUCHER_DISCOVERY_OPERATION.productionGatewayAllowed
  ) {
    throw new Error('Voucher discovery operation safety classification is invalid.');
  }

  const xml = new TallyXmlRequestBuilder().buildEmbeddedCollection(
    buildVoucherCollectionRequestSpec({
      companyName: request.companyName,
      dateFrom: request.dateFrom,
      dateTo: request.dateTo,
    }),
  );
  const validation = validateTallyRequestXml(xml, VOUCHER_DISCOVERY_MAX_REQUEST_BYTES);
  if (
    validation.requestType !== 'EXPORT' ||
    validation.requestKind !== 'COLLECTION' ||
    validation.collectionId !== VOUCHER_DISCOVERY_OPERATION.provisionalTallyId
  ) {
    throw new Error('Generated request does not match the discovery-local allowlist.');
  }
  return {
    operationId: VOUCHER_DISCOVERY_OPERATION_ID,
    xml,
    companyToken: fixtureCompanyToken(request.companyName),
    dateFrom: request.dateFrom,
    dateTo: request.dateTo,
  };
}
