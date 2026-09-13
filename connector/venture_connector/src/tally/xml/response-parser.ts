import {
  resolveXmlParserLimits,
  type XmlParserOptions,
} from './response-parser-limits.js';
import { XmlParseError } from './response-parser-errors.js';

export { XmlParseError, toPrivacySafeXmlParseDetails } from './response-parser-errors.js';
export {
  DEFAULT_XML_PARSER_MAX_DEPTH,
  DEFAULT_XML_PARSER_MAX_NODE_COUNT,
  type XmlParserLimits,
  type XmlParserOptions,
} from './response-parser-limits.js';

/** Parsed XML node — generic tree for framework consumers; not a domain entity. */
export interface ParsedXmlNode {
  readonly name: string;
  readonly attributes: Readonly<Record<string, string>>;
  readonly text?: string;
  readonly children: readonly ParsedXmlNode[];
}

export interface ParsedXmlDocument {
  readonly root: ParsedXmlNode;
  readonly rawXml: string;
  /**
   * Count of XML-1.0-illegal C0 control characters (literal bytes or numeric character
   * references, e.g. Tally's known `&#4;` export artifact) removed before parsing. A
   * count only — never the removed characters' surrounding content — so this is safe to
   * log as telemetry without exposing business data. Zero when nothing was removed.
   */
  readonly illegalCharactersSanitized: number;
}

export type XmlNodeHandler = (node: ParsedXmlNode, document: ParsedXmlDocument) => void;

interface MutableParsedXmlNode {
  name: string;
  attributes: Record<string, string>;
  text?: string;
  children: MutableParsedXmlNode[];
}

interface ParseContext {
  nodeCount: number;
  readonly maxDepth: number;
  readonly maxNodeCount: number;
}

/**
 * Framework parser for Tally XML envelopes.
 * Performs structural parsing only — business data mappers register handlers separately.
 */
export class TallyXmlResponseParser {
  private readonly handlers = new Map<string, XmlNodeHandler[]>();

  registerHandler(nodeName: string, handler: XmlNodeHandler): void {
    const normalized = nodeName.toUpperCase();
    const list = this.handlers.get(normalized) ?? [];
    list.push(handler);
    this.handlers.set(normalized, list);
  }

  parse(rawXml: string, options?: XmlParserOptions): ParsedXmlDocument {
    const limits = resolveXmlParserLimits(options);
    if (rawXml.length > limits.maxBytes) {
      throw new XmlParseError('xml_oversized', 'Invalid XML: document exceeds the allowed byte limit.', {
        maxBytes: limits.maxBytes,
      });
    }
    // Known Tally export artifact (TD-001): illegal XML 1.0 C0 control characters, most
    // commonly the numeric reference `&#4;`, occasionally reaching the parser in free-text
    // fields (Voucher narration/party name, Group parent, etc). These are removed here,
    // narrowly, before structural parsing -- structural validity (below) remains strict.
    const { sanitized, illegalCharactersSanitized } = sanitizeXml10IllegalCharacters(rawXml);
    const ctx: ParseContext = {
      nodeCount: 0,
      maxDepth: limits.maxDepth,
      maxNodeCount: limits.maxNodeCount,
    };

    let result: { node?: ParsedXmlNode; nextIndex: number };
    let trimmed: string;
    try {
      trimmed = sanitized.trim();
      if (!trimmed.startsWith('<')) {
        throw new XmlParseError('xml_malformed', 'Invalid XML: document does not start with a tag');
      }

      result = parseElement(trimmed, 0, ctx, 0);
      if (!result.node) {
        throw new XmlParseError('xml_malformed', 'Invalid XML: unable to parse root element');
      }

      const trailing = trimmed.slice(result.nextIndex);
      if (trailing.trim().length > 0) {
        throw new XmlParseError('xml_trailing_content', 'Invalid XML: trailing content after root element', {
          trailingLength: trailing.trim().length,
        });
      }
    } catch (error) {
      // Sanitization telemetry must survive even when the document is still structurally
      // invalid after sanitization -- distinguishing "sanitized but still broken elsewhere"
      // from "nothing to sanitize, broken for an unrelated reason" is exactly the diagnostic
      // this exists to preserve.
      if (error instanceof XmlParseError) {
        throw new XmlParseError(error.reason, error.message, {
          ...(error.details ?? {}),
          illegalCharactersSanitized,
        });
      }
      throw error;
    }

    const document: ParsedXmlDocument = {
      root: result.node,
      rawXml,
      illegalCharactersSanitized,
    };
    this.walk(result.node, document);
    return document;
  }

  findFirst(document: ParsedXmlDocument, nodeName: string): ParsedXmlNode | undefined {
    const target = nodeName.toUpperCase();
    return this.findNode(document.root, target);
  }

  findAll(document: ParsedXmlDocument, nodeName: string): ParsedXmlNode[] {
    const target = nodeName.toUpperCase();
    const found: ParsedXmlNode[] = [];
    this.collectNodes(document.root, target, found);
    return found;
  }

  getText(node: ParsedXmlNode | undefined): string | undefined {
    return node?.text?.trim() || undefined;
  }

  private walk(node: ParsedXmlNode, document: ParsedXmlDocument): void {
    const handlers = this.handlers.get(node.name.toUpperCase()) ?? [];
    for (const handler of handlers) {
      handler(node, document);
    }
    for (const child of node.children) {
      this.walk(child, document);
    }
  }

  private findNode(node: ParsedXmlNode, target: string): ParsedXmlNode | undefined {
    if (node.name.toUpperCase() === target) {
      return node;
    }
    for (const child of node.children) {
      const match = this.findNode(child, target);
      if (match) return match;
    }
    return undefined;
  }

  private collectNodes(node: ParsedXmlNode, target: string, found: ParsedXmlNode[]): void {
    if (node.name.toUpperCase() === target) {
      found.push(node);
    }
    for (const child of node.children) {
      this.collectNodes(child, target, found);
    }
  }
}

/**
 * Removes XML-1.0-illegal C0 control characters -- both literal bytes and numeric
 * character references (e.g. Tally's documented `&#4;` export artifact, TD-001) --
 * before structural parsing. Legal XML whitespace (#x9 TAB, #xA LF, #xD CR) is always
 * preserved, in either representation. Every other character class (ordinary Unicode,
 * business text) is left untouched. This is deliberately narrow: it does not repair
 * structural XML errors, invalid nesting, undeclared entities, or any other malformed
 * markup -- structural parsing after this step remains fully strict.
 */
function sanitizeXml10IllegalCharacters(rawXml: string): {
  sanitized: string;
  illegalCharactersSanitized: number;
} {
  let illegalCharactersSanitized = 0;

  const withoutIllegalReferences = rawXml.replace(
    /&#(?:x([0-9a-fA-F]+)|(\d+));/g,
    (match, hex: string | undefined, dec: string | undefined) => {
      const value = Number.parseInt(hex ?? dec ?? '', hex ? 16 : 10);
      if (Number.isNaN(value) || isXml10Character(value)) {
        return match;
      }
      illegalCharactersSanitized += 1;
      return '';
    },
  );

  let sanitized = '';
  for (const char of withoutIllegalReferences) {
    const codePoint = char.codePointAt(0);
    if (codePoint !== undefined && !isXml10Character(codePoint)) {
      illegalCharactersSanitized += 1;
      continue;
    }
    sanitized += char;
  }

  return { sanitized, illegalCharactersSanitized };
}

function isXml10Character(value: number): boolean {
  return value === 0x9 ||
    value === 0xa ||
    value === 0xd ||
    (value >= 0x20 && value <= 0xd7ff) ||
    (value >= 0xe000 && value <= 0xfffd) ||
    (value >= 0x10000 && value <= 0x10ffff);
}

function parseElement(
  xml: string,
  startIndex: number,
  ctx: ParseContext,
  parentDepth: number,
): { node?: ParsedXmlNode; nextIndex: number } {
  const openStart = xml.indexOf('<', startIndex);
  if (openStart === -1) {
    return { nextIndex: xml.length };
  }

  if (xml.startsWith('<?', openStart)) {
    const close = xml.indexOf('?>', openStart);
    return parseElement(xml, close === -1 ? xml.length : close + 2, ctx, parentDepth);
  }

  const openEnd = xml.indexOf('>', openStart);
  if (openEnd === -1) {
    throw new XmlParseError('xml_malformed', 'Invalid XML: unclosed tag');
  }

  const openTagContent = xml.slice(openStart + 1, openEnd).trim();
  if (openTagContent.startsWith('/')) {
    return { nextIndex: openEnd + 1 };
  }

  const elementDepth = parentDepth + 1;
  if (elementDepth > ctx.maxDepth) {
    throw new XmlParseError('xml_max_depth_exceeded', 'Invalid XML: maximum nesting depth exceeded', {
      maxDepth: ctx.maxDepth,
      observedDepth: elementDepth,
    });
  }

  allocateElementNode(ctx);

  if (openTagContent.endsWith('/')) {
    const selfClosing = parseTagName(openTagContent.slice(0, -1));
    return {
      node: {
        name: selfClosing,
        attributes: parseAttributes(openTagContent.slice(0, -1)),
        children: [],
      },
      nextIndex: openEnd + 1,
    };
  }

  const tagName = parseTagName(openTagContent);
  const attributes = parseAttributes(openTagContent);
  const node: MutableParsedXmlNode = {
    name: tagName,
    attributes,
    children: [],
  };

  const closeTag = `</${tagName}>`;
  const closeIndex = xml.toUpperCase().indexOf(closeTag.toUpperCase(), openEnd + 1);
  if (closeIndex === -1) {
    throw new XmlParseError('xml_malformed', 'Invalid XML: missing closing tag');
  }

  const inner = xml.slice(openEnd + 1, closeIndex);
  const childOnly = inner.trim();
  if (childOnly && !childOnly.startsWith('<')) {
    node.text = decodeXmlEntities(childOnly);
    return { node, nextIndex: closeIndex + closeTag.length };
  }

  let cursor = openEnd + 1;
  while (cursor < closeIndex) {
    const parsed = parseElement(xml, cursor, ctx, elementDepth);
    if (parsed.node) {
      node.children.push(parsed.node as MutableParsedXmlNode);
    }
    if (parsed.nextIndex <= cursor) {
      break;
    }
    cursor = parsed.nextIndex;
  }

  return { node, nextIndex: closeIndex + closeTag.length };
}

function allocateElementNode(ctx: ParseContext): void {
  ctx.nodeCount += 1;
  if (ctx.nodeCount > ctx.maxNodeCount) {
    throw new XmlParseError('xml_max_node_count_exceeded', 'Invalid XML: maximum node count exceeded', {
      maxNodeCount: ctx.maxNodeCount,
      observedNodeCount: ctx.nodeCount,
    });
  }
}

function parseTagName(raw: string): string {
  const trimmed = raw.trim();
  const spaceIndex = trimmed.search(/\s/);
  const tag = spaceIndex === -1 ? trimmed : trimmed.slice(0, spaceIndex);
  return tag.toUpperCase();
}

function parseAttributes(raw: string): Record<string, string> {
  const attributes: Record<string, string> = {};
  const attrPattern = /([A-Za-z_][\w:.-]*)\s*=\s*"([^"]*)"/g;
  for (const match of raw.matchAll(attrPattern)) {
    const key = match[1];
    const value = match[2];
    if (key) {
      attributes[key.toUpperCase()] = decodeXmlEntities(value);
    }
  }
  return attributes;
}

function decodeXmlEntities(value: string): string {
  return value
    .replaceAll('&lt;', '<')
    .replaceAll('&gt;', '>')
    .replaceAll('&quot;', '"')
    .replaceAll('&apos;', "'")
    .replaceAll('&amp;', '&')
    .trim();
}

/** Test and diagnostic helper — maximum element depth (root depth = 1). */
export function measureParsedXmlMaxDepth(node: ParsedXmlNode, depth = 1): number {
  if (node.children.length === 0) {
    return depth;
  }
  return Math.max(...node.children.map((child) => measureParsedXmlMaxDepth(child, depth + 1)));
}

/** Test and diagnostic helper — total allocated element nodes. */
export function countParsedXmlNodes(node: ParsedXmlNode): number {
  return 1 + node.children.reduce((sum, child) => sum + countParsedXmlNodes(child), 0);
}
