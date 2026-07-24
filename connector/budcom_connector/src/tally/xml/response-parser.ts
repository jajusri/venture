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
    const ctx: ParseContext = {
      nodeCount: 0,
      maxDepth: limits.maxDepth,
      maxNodeCount: limits.maxNodeCount,
    };
    const trimmed = rawXml.trim();
    if (!trimmed.startsWith('<')) {
      throw new XmlParseError('xml_malformed', 'Invalid XML: document does not start with a tag');
    }

    const result = parseElement(trimmed, 0, ctx, 0);
    if (!result.node) {
      throw new XmlParseError('xml_malformed', 'Invalid XML: unable to parse root element');
    }

    const trailing = trimmed.slice(result.nextIndex);
    if (trailing.trim().length > 0) {
      throw new XmlParseError('xml_trailing_content', 'Invalid XML: trailing content after root element', {
        trailingLength: trailing.trim().length,
      });
    }

    const document: ParsedXmlDocument = { root: result.node, rawXml };
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
