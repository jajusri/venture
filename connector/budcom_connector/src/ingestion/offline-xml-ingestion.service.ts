import { readFile } from 'node:fs/promises';

import type { Logger } from '../infrastructure/logging/logger.js';
import { AppError, ErrorCodes } from '../infrastructure/errors/app-error.js';
import type { ServiceStatus } from '../core/types.js';
import { TallyXmlResponseParser, type ParsedXmlNode } from '../tally/xml/response-parser.js';
import type { XmlImportService } from '../services/interfaces/xml-import.js';

function countNodes(node: ParsedXmlNode): number {
  return 1 + node.children.reduce((sum, child) => sum + countNodes(child), 0);
}

/**
 * OFFLINE Budcom XML FILE ingestion.
 *
 * ARCHITECTURAL BOUNDARY (Phase 3): this module ingests user-selected XML files
 * that were exported elsewhere. It parses and validates files locally. It has
 * NO access to the live Tally transport, connection manager, read gateway,
 * fetch client, or any network socket, and it never issues a Tally IMPORT
 * request. "Import" here means importing files INTO Budcom, not into Tally.
 *
 * The dependency-boundary architecture test enforces that this file (and the
 * `src/ingestion` directory) never imports from `src/tally/transport`,
 * `src/tally/connection`, or `src/tally/gateway`.
 */
export interface OfflineIngestionResult {
  readonly sourceLabel: string;
  readonly nodeCount: number;
  readonly byteLength: number;
}

export class OfflineXmlIngestionService implements XmlImportService {
  private running = false;

  constructor(
    private readonly parser: TallyXmlResponseParser,
    private readonly logger: Logger,
  ) {}

  async start(): Promise<void> {
    this.running = true;
    this.logger.info('Offline XML ingestion framework started');
  }

  async stop(): Promise<void> {
    this.running = false;
    this.logger.info('Offline XML ingestion framework stopped');
  }

  isRunning(): boolean {
    return this.running;
  }

  getParser(): TallyXmlResponseParser {
    return this.parser;
  }

  /** Ingest an XML export file from local disk. Never contacts Tally. */
  async ingestFile(filePath: string): Promise<OfflineIngestionResult> {
    this.assertRunning();
    const content = await readFile(filePath, 'utf8');
    return this.ingestString(content, filePath);
  }

  /** Ingest an in-memory XML export string. Never contacts Tally. */
  ingestString(xml: string, sourceLabel = 'inline'): OfflineIngestionResult {
    this.assertRunning();
    if (!xml.trim()) {
      throw new AppError(ErrorCodes.VALIDATION_ERROR, 'Offline XML file is empty', 400);
    }
    const document = this.parser.parse(xml);
    return {
      sourceLabel,
      nodeCount: countNodes(document.root),
      byteLength: Buffer.byteLength(xml, 'utf8'),
    };
  }

  getStatus(): ServiceStatus {
    return {
      name: 'OfflineXmlIngestion',
      running: this.running,
      ready: this.running,
      message: 'Offline XML ingestion ready (no live Tally access)',
    };
  }

  private assertRunning(): void {
    if (!this.running) {
      throw new AppError(
        ErrorCodes.SERVICE_UNAVAILABLE,
        'Offline XML ingestion is not running',
        503,
      );
    }
  }
}
