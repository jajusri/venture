import type { Logger } from '../../infrastructure/logging/logger.js';
import { AppError, ErrorCodes } from '../../infrastructure/errors/app-error.js';
import type { TallyConnectionManager } from '../connection/tally-connection-manager.js';
import type { TallyXmlRequestBuilder } from '../xml/request-builder.js';
import {
  ApprovedOperationId,
  getApprovedOperation,
  type ApprovedOperation,
} from '../registry/operation-registry.js';

/**
 * The ONLY application-facing way to talk to Tally.
 *
 * Callers submit a strongly typed, pre-approved read request identified by an
 * {@link ApprovedOperationId}. There is deliberately:
 *  - no raw-XML parameter,
 *  - no caller-supplied Tally method/type,
 *  - no caller-supplied collection ID or endpoint/port,
 *  - no public generic `send`.
 *
 * XML is constructed internally from the immutable operation registry contract.
 * The raw transport is private to the adapter composition root and is never
 * handed to extractors, routes, services, or background workers.
 */
export interface ApprovedReadRequest {
  readonly operationId: ApprovedOperationId;
  readonly companyName?: string;
  readonly signal?: AbortSignal;
}

export interface ApprovedReadResult {
  readonly operationId: ApprovedOperationId;
  readonly rawXml: string;
  readonly byteLength: number;
  readonly durationMs: number;
}

export interface TallyReadGatewayDeps {
  readonly connectionManager: TallyConnectionManager;
  readonly requestBuilder: TallyXmlRequestBuilder;
  readonly logger: Logger;
}

export class TallyReadGateway {
  constructor(private readonly deps: TallyReadGatewayDeps) {}

  isReady(): boolean {
    return this.deps.connectionManager.isRunning();
  }

  async executeApprovedRead(request: ApprovedReadRequest): Promise<ApprovedReadResult> {
    const operation: ApprovedOperation | undefined = getApprovedOperation(request.operationId);
    if (!operation) {
      // UNKNOWN operation id => fail closed.
      throw new AppError(
        ErrorCodes.VALIDATION_ERROR,
        `Unknown Tally operation: ${String(request.operationId)}`,
        403,
      );
    }

    if (operation.requiresCompany && !request.companyName) {
      throw new AppError(
        ErrorCodes.VALIDATION_ERROR,
        `Operation ${operation.operationId} requires a company context`,
        400,
      );
    }

    const spec = operation.render({ companyName: request.companyName });
    const xml = this.deps.requestBuilder.build(spec);

    const isCollection = operation.requestKind === 'Collection';
    const exchange = await this.deps.connectionManager.exchange(xml, {
      collectionId: isCollection ? operation.tallyId : undefined,
      reportId: isCollection ? undefined : operation.tallyId,
      timeoutMs: operation.timeoutMs,
      signal: request.signal,
    });

    if (exchange.response.byteLength > operation.maxResponseBytes) {
      throw new AppError(
        ErrorCodes.SERVICE_UNAVAILABLE,
        `Response for ${operation.operationId} exceeds contract maximum (${operation.maxResponseBytes} bytes)`,
        503,
        { byteLength: exchange.response.byteLength, maxResponseBytes: operation.maxResponseBytes },
      );
    }

    return {
      operationId: operation.operationId,
      rawXml: exchange.rawXml,
      byteLength: exchange.response.byteLength,
      durationMs: exchange.response.durationMs,
    };
  }
}
