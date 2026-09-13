import type { ParsedXmlDocument, ParsedXmlNode, TallyXmlResponseParser } from '../xml/response-parser.js';
import type { DiscoveredCompany } from '../core/types.js';
import type { CompanyDiscoveryParseResult } from '../contracts/company-discovery-contract.js';
import { isCountMetadata, normalizeText, slugify } from '../../extraction/normalization/strings.js';

/**
 * Extracts company discovery metadata from a Tally "List of Companies" response.
 * This is structural/discovery parsing — not ledger, voucher, or inventory data.
 */
export class CompanyDiscoveryParser {
  constructor(private readonly parser: TallyXmlResponseParser) {}

  parseCompanies(document: ParsedXmlDocument): CompanyDiscoveryParseResult {
    const candidates: DiscoveredCompany[] = [];
    let skippedRecords = 0;
    let recordsMissingIdentity = 0;

    const companyNodes = this.parser.findAll(document, 'COMPANY');
    for (const node of companyNodes) {
      const mapped = this.mapCompanyNode(node);
      if (!mapped) {
        if (this.isMetadataCountNode(node)) {
          skippedRecords += 1;
        } else if (this.looksLikeCompanyNode(node)) {
          recordsMissingIdentity += 1;
        } else {
          skippedRecords += 1;
        }
        continue;
      }
      candidates.push(mapped);
    }

    if (candidates.length === 0) {
      const collectionNodes = this.parser.findAll(document, 'COLLECTION');
      for (const collection of collectionNodes) {
        for (const child of collection.children) {
          if (child.name.toUpperCase() !== 'COMPANY') continue;
          const mapped = this.mapCompanyNode(child);
          if (!mapped) {
            recordsMissingIdentity += 1;
            continue;
          }
          candidates.push(mapped);
        }
      }
    }

    const { companies, duplicateRecordsRemoved } = dedupeCompanies(candidates);

    return {
      companies,
      skippedRecords,
      duplicateRecordsRemoved,
      recordsMissingIdentity,
    };
  }

  private mapCompanyNode(node: ParsedXmlNode): DiscoveredCompany | undefined {
    const name = this.resolveRawName(node);
    if (!name || name.toUpperCase() === 'COMPANY' || isCountMetadata(name)) {
      return undefined;
    }

    return {
      id: slugify(name),
      name,
      startingFrom: normalizeText(this.parser.getText(this.findChild(node, 'STARTINGFROM'))),
      booksFrom: normalizeText(this.parser.getText(this.findChild(node, 'BOOKSFROM'))),
      baseCurrency: normalizeText(this.parser.getText(this.findChild(node, 'BASECURRENCY'))),
    };
  }

  private resolveRawName(node: ParsedXmlNode): string | undefined {
    return (
      normalizeText(this.parser.getText(this.findChild(node, 'NAME'))) ??
      normalizeText(node.attributes.NAME) ??
      normalizeText(node.text)
    );
  }

  private isMetadataCountNode(node: ParsedXmlNode): boolean {
    const name = this.resolveRawName(node);
    return name !== undefined && isCountMetadata(name);
  }

  private looksLikeCompanyNode(node: ParsedXmlNode): boolean {
    return (
      node.name.toUpperCase() === 'COMPANY' ||
      node.attributes.NAME !== undefined ||
      this.findChild(node, 'NAME') !== undefined
    );
  }

  private findChild(node: ParsedXmlNode, name: string): ParsedXmlNode | undefined {
    const target = name.toUpperCase();
    return node.children.find((child) => child.name.toUpperCase() === target);
  }
}

function dedupeCompanies(companies: DiscoveredCompany[]): {
  companies: DiscoveredCompany[];
  duplicateRecordsRemoved: number;
} {
  const seen = new Set<string>();
  const result: DiscoveredCompany[] = [];
  let duplicateRecordsRemoved = 0;

  for (const company of companies) {
    if (seen.has(company.id)) {
      duplicateRecordsRemoved += 1;
      continue;
    }
    seen.add(company.id);
    result.push(company);
  }

  return { companies: result, duplicateRecordsRemoved };
}
