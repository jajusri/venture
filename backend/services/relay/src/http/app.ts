import Fastify, { type FastifyInstance } from 'fastify';
import { AcceptRelaySubmission } from '../application/accept-submission.js';
import type { RelayAcceptanceIssuer } from '../application/acceptance-evidence.js';
import type { RelaySubmissionVerifier } from '../application/accept-submission.js';
import { FetchRecipientMailbox } from '../application/fetch-mailbox.js';
import type { RelayMailboxVerifier } from '../application/fetch-mailbox.js';
import { RecordRelayAcknowledgement } from '../application/record-acknowledgement.js';
import type { RelayAcknowledgementVerifier } from '../application/record-acknowledgement.js';
import type { RelayIngressLimiter } from '../application/relay-protections.js';
import { RelayServiceError, type RelayErrorBody } from '../errors.js';
import type { RelayRepository } from '../persistence/relay-repository.js';
import { mapRelayAcknowledgementBody, type RelayAcknowledgementBody } from './map-acknowledgement.js';
import { mapRelayMailboxFetchBody, type RelayMailboxFetchBody } from './map-mailbox-fetch.js';
import { mapRelaySubmissionBody, type RelaySubmissionBody } from './map-submission.js';

const MAX_BODY_BYTES = 384 * 1024;

export function buildRelayService(options: {
  repository: RelayRepository;
  verifier: RelaySubmissionVerifier;
  mailboxVerifier: RelayMailboxVerifier;
  acknowledgementVerifier: RelayAcknowledgementVerifier;
  ingressLimiter?: RelayIngressLimiter;
  issuer: RelayAcceptanceIssuer;
  now?: () => Date;
}): FastifyInstance {
  const app = Fastify({ logger: true, bodyLimit: MAX_BODY_BYTES });
  const accept = new AcceptRelaySubmission(options.repository, options.verifier, options.issuer, options.ingressLimiter, options.now);
  const fetchMailbox = new FetchRecipientMailbox(options.repository, options.mailboxVerifier);
  const recordAck = new RecordRelayAcknowledgement(options.repository, options.acknowledgementVerifier, options.now);
  app.get('/health', () => ({ status: 'ok', service: 'budcom-relay' }));
  app.post<{ Body: RelaySubmissionBody }>('/v1/relay/envelopes', async (request) => {
    const stored = await accept.execute(mapRelaySubmissionBody(request.body ?? {}, options.now?.() ?? new Date()));
    const acceptance = stored.acceptance;
    return {
      status: acceptance.status,
      acceptanceId: acceptance.acceptanceId,
      envelopeId: acceptance.envelopeId,
      objectType: acceptance.objectType,
      objectId: acceptance.objectId,
      objectVersion: acceptance.objectVersion,
      senderBusinessId: acceptance.senderBusinessId,
      recipientBusinessId: acceptance.recipientBusinessId,
      acceptedAt: acceptance.acceptedAt.toISOString(),
      relayId: acceptance.relayId,
      evidenceProfile: acceptance.evidenceProfile,
      evidence: Buffer.from(acceptance.evidence).toString('base64'),
    };
  });
  app.post<{ Body: RelayMailboxFetchBody }>('/v1/relay/mailboxes/fetch', async (request) => {
    const page = await fetchMailbox.execute(mapRelayMailboxFetchBody(request.body ?? {}));
    return {
      recipientBusinessId: page.recipient.businessId,
      mailboxId: page.recipient.mailboxId,
      nextCursor: page.nextCursor,
      items: page.items.map((item) => ({
        envelopeId: item.envelopeId,
        mailboxSequence: item.mailboxSequence,
        objectType: item.objectType,
        objectId: item.objectId,
        objectVersion: item.objectVersion,
        senderBusinessId: item.senderBusinessId,
        senderActorId: item.senderActorId,
        senderDeviceId: item.senderDeviceId,
        status: item.status,
        acceptedAt: item.acceptedAt.toISOString(),
        acceptanceId: item.acceptanceId,
        authenticatedEnvelope: Buffer.from(item.authenticatedEnvelope).toString('base64'),
      })),
    };
  });
  app.post<{ Body: RelayAcknowledgementBody }>('/v1/relay/acknowledgements', async (request) => {
    const acknowledgement = await recordAck.execute(mapRelayAcknowledgementBody(request.body ?? {}, options.now?.() ?? new Date()));
    return {
      envelopeId: acknowledgement.envelopeId,
      recipientBusinessId: acknowledgement.recipientBusinessId,
      recipientDeviceId: acknowledgement.recipientDeviceId,
      receivedAt: acknowledgement.receivedAt.toISOString(),
      status: 'delivered',
    };
  });
  app.setErrorHandler((error, request, reply) => {
    const known = error instanceof RelayServiceError;
    const mapped = error instanceof Error ? error : new Error('Relay request failed');
    const status = known ? error.statusCode : mapUnknown(mapped);
    if (known && error.retryAfterMs) void reply.header('retry-after', Math.ceil(error.retryAfterMs / 1000));
    const body: RelayErrorBody = {
      error: {
        code: known ? error.code : status === 400 ? 'invalid_submission' : 'internal_error',
        message: known ? error.message : publicMessage(mapped, status),
        requestId: request.id,
      },
    };
    void reply.status(status).send(body);
  });
  return app;
}

function mapUnknown(error: Error): number {
  const message = error.message;
  if (message.includes('not found')) return 404;
  if (message.includes('authority rejected') || message.includes('binding mismatch')) return 403;
  if (message.includes('required') || message.includes('bounds') || message.includes('protocol') || message.includes('must contain') || message.includes('cursor')) return 400;
  return 500;
}

function publicMessage(error: Error, status: number): string {
  if (status === 409) return error.message;
  if (status === 403) return 'Authenticated relay authority rejected';
  if (status === 400) return 'Relay submission was rejected';
  return 'The Relay Service could not process the request.';
}
