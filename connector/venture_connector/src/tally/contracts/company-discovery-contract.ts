import type { DataQuality } from '../../extraction/core/types.js';
import type { DiscoveredCompany } from '../core/types.js';
import type {
  CompanyDiscoveryStatus,
  ErpCompanySummary,
} from '../../erp/ports/company-discovery.js';
import { assessEnvelope } from './response-contract.js';

export const COMPANY_DISCOVERY_RESPONSE_CONTRACT_VERSION = '1';

export interface CompanyDiscoveryParseResult {
  readonly companies: readonly DiscoveredCompany[];
  readonly skippedRecords: number;
  readonly duplicateRecordsRemoved: number;
  readonly recordsMissingIdentity: number;
}

export interface CompanyDiscoveryAssessment {
  readonly status: CompanyDiscoveryStatus;
  readonly dataQuality?: DataQuality;
  readonly reason?: string;
}

/**
 * Assess a parsed company-discovery payload against the response contract.
 * Callers must run {@link assessEnvelope} on raw XML before parsing.
 */
export function assessCompanyDiscovery(
  parseResult: CompanyDiscoveryParseResult,
): CompanyDiscoveryAssessment {
  const { companies, recordsMissingIdentity, skippedRecords } = parseResult;

  if (companies.length > 0) {
    if (recordsMissingIdentity > 0 || skippedRecords > 0) {
      return {
        status: 'SUCCESS',
        dataQuality: {
          status: 'INCOMPLETE',
          reason:
            `Recovered ${companies.length} companies but ${recordsMissingIdentity} record(s) lacked ` +
            'a required identity field and were excluded',
        },
      };
    }
    return { status: 'SUCCESS' };
  }

  if (recordsMissingIdentity > 0) {
    return {
      status: 'INCOMPLETE',
      dataQuality: {
        status: 'INCOMPLETE',
        reason: 'Company nodes were present but none had a usable identity (NAME)',
      },
      reason: 'Company nodes were present but none had a usable identity (NAME)',
    };
  }

  return {
    status: 'EMPTY',
    dataQuality: {
      status: 'EMPTY',
      reason: 'Tally returned a valid envelope with no discoverable companies',
    },
    reason: 'No companies are currently available from Tally',
  };
}

/** Pre-parse envelope validation for company discovery responses. */
export function assessCompanyDiscoveryEnvelope(rawXml: string): DataQuality | undefined {
  return assessEnvelope(rawXml);
}

export function mapDiscoveredToErpSummary(company: DiscoveredCompany): ErpCompanySummary {
  return {
    id: company.id,
    name: company.name,
    financialYear: company.startingFrom,
    booksFrom: company.booksFrom,
    baseCurrency: company.baseCurrency,
  };
}
