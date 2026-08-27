import Fastify, { type FastifyInstance } from 'fastify';
import { TrustServiceError, type ServiceErrorBody } from './errors.js';
import type { VerificationKeyDirectory } from './application/verification-keys.js';
export function buildTrustService(options: { verificationKeys?: VerificationKeyDirectory; now?: () => Date } = {}): FastifyInstance {
  const app = Fastify({ logger: true });
  app.get('/health', () => ({ status: 'ok', service: 'budcom-trust' }));
  if (options.verificationKeys) app.get<{ Params: { issuerId: string } }>('/v1/trust/issuers/:issuerId/verification-keys', async (request, reply) => {
    const keys = await options.verificationKeys!.list(request.params.issuerId, options.now?.() ?? new Date());
    void reply.header('cache-control', 'public, max-age=300, stale-if-error=3600');
    return { version: 1, issuerId: request.params.issuerId, keys: keys.map((key) => ({ ...key, validFrom: key.validFrom.toISOString(), validUntil: key.validUntil?.toISOString() })) };
  });
  app.setErrorHandler((error, request, reply) => {
    const known = error instanceof TrustServiceError;
    const body: ServiceErrorBody = { error: { code: known ? error.code : 'internal_error', message: known ? error.message : 'The Trust Service could not process the request.', requestId: request.id } };
    void reply.status(known ? error.statusCode : 500).send(body);
  });
  return app;
}
