import type { ParsedXmlDocument, ParsedXmlNode, TallyXmlResponseParser } from '../xml/response-parser.js';
import type { DiscoveredCompany } from '../core/types.js';

/**
 * Extracts company discovery metadata from a Tally "List of Companies" response.
 * This is structural/discovery parsing — not ledger, voucher, or inventory data.
 */
export class CompanyDiscoveryParser {
  constructor(private readonly parser: TallyXmlResponseParser) {}

  parseCompanies(document: ParsedXmlDocument): DiscoveredCompany[] {
    const companies: DiscoveredCompany[] = [];
    const companyNodes = this.parser.findAll(document, 'COMPANY');

    for (const node of companyNodes) {
      const nameFromChild = this.parser.getText(this.findChild(node, 'NAME'));
      const nameFromAttribute = node.attributes.NAME;
      const name = nameFromChild ?? nameFromAttribute ?? node.text;
      if (!name || name === 'COMPANY' || isCountMetadata(name)) continue;

      companies.push({
        id: slugifyCompanyId(name),
        name,
        startingFrom: this.parser.getText(this.findChild(node, 'STARTINGFROM')),
        booksFrom: this.parser.getText(this.findChild(node, 'BOOKSFROM')),
      });
    }

    if (companies.length === 0) {
      const collectionNodes = this.parser.findAll(document, 'COLLECTION');
      for (const collection of collectionNodes) {
        for (const child of collection.children) {
          if (child.name.toUpperCase() !== 'COMPANY') continue;
          const name =
            this.parser.getText(this.findChild(child, 'NAME')) ??
            child.attributes.NAME ??
            this.parser.getText(child);
          if (!name || isCountMetadata(name)) continue;
          companies.push({
            id: slugifyCompanyId(name),
            name,
          });
        }
      }
    }

    return dedupeCompanies(companies);
  }

  private findChild(node: ParsedXmlNode, name: string): ParsedXmlNode | undefined {
    const target = name.toUpperCase();
    return node.children.find((child) => child.name.toUpperCase() === target);
  }
}

function slugifyCompanyId(name: string): string {
  return name
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');
}

function isCountMetadata(value: string): boolean {
  return /^\d+$/.test(value.trim());
}

function dedupeCompanies(companies: DiscoveredCompany[]): DiscoveredCompany[] {
  const seen = new Set<string>();
  const result: DiscoveredCompany[] = [];
  for (const company of companies) {
    if (seen.has(company.id)) continue;
    seen.add(company.id);
    result.push(company);
  }
  return result;
}
