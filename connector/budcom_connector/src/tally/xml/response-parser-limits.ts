import { XmlParseError } from './response-parser-errors.js';

/**
 * Bounded XML parser limits for Tally response parsing.
 *
 * Evidence basis (operation-registry, master-data fixtures, controlled pilot):
 * - Rich ledger/stock envelope max element depth: 6 (ENVELOPE→…→field nodes)
 * - Live rich ledger export: ~922 entities / ~618 KiB
 * - Live rich stock export: ~1502 entities / ~963 KiB
 * - Per-ledger rich node budget: ~9 element nodes (LEDGER + 8 FETCH fields)
 * - Per-stock rich node budget: ~7 element nodes (STOCKITEM + fields)
 * - Estimated live node totals: ledger ~8.3k, stock ~10.5k element nodes
 * - Transport byte cap (rich master collections): 1 MiB (complementary, not replaced)
 *
 * Defaults apply headroom above live evidence while remaining finite.
 */

/** Root element depth is 1; deepest known committed fixture depth is 6. */
export const DEFAULT_XML_PARSER_MAX_DEPTH = 64;

/**
 * Upper bound on allocated element nodes per parse.
 * ~3× observed live stock node total with room for richer field exports.
 */
export const DEFAULT_XML_PARSER_MAX_NODE_COUNT = 32_768;

/** Maximum depth permitted for any parse invocation, including test overrides. */
export const APPROVED_XML_PARSER_MAX_DEPTH = DEFAULT_XML_PARSER_MAX_DEPTH;

/** Maximum node count permitted for any parse invocation, including test overrides. */
export const APPROVED_XML_PARSER_MAX_NODE_COUNT = DEFAULT_XML_PARSER_MAX_NODE_COUNT;

export interface XmlParserLimits {
  readonly maxDepth: number;
  readonly maxNodeCount: number;
}

export type XmlParserOptions = Partial<XmlParserLimits>;

export function resolveXmlParserLimits(options?: XmlParserOptions): XmlParserLimits {
  return {
    maxDepth: resolveParserLimitOption(
      'maxDepth',
      options?.maxDepth,
      DEFAULT_XML_PARSER_MAX_DEPTH,
      APPROVED_XML_PARSER_MAX_DEPTH,
    ),
    maxNodeCount: resolveParserLimitOption(
      'maxNodeCount',
      options?.maxNodeCount,
      DEFAULT_XML_PARSER_MAX_NODE_COUNT,
      APPROVED_XML_PARSER_MAX_NODE_COUNT,
    ),
  };
}

function resolveParserLimitOption(
  optionName: 'maxDepth' | 'maxNodeCount',
  value: number | undefined,
  defaultValue: number,
  approvedMaximum: number,
): number {
  if (value === undefined) {
    return defaultValue;
  }

  if (!Number.isFinite(value) || !Number.isInteger(value) || value < 1 || value > approvedMaximum) {
    throw new XmlParseError('xml_invalid_parser_limit', 'Invalid XML parser limit option.', {
      optionName,
      approvedMaximum,
      ...(Number.isFinite(value) ? { providedValue: value } : {}),
    });
  }

  return value;
}
