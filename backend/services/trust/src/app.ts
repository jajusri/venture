import Fastify, { type FastifyInstance } from 'fastify';
import { TrustServiceError, type ServiceErrorBody } from './errors.js';
export function buildTrustService(): FastifyInstance {
  const app = Fastify({ logger: true });
  app.get('/health', () => ({ status: 'ok', service: 'budcom-trust' }));
  app.setErrorHandler((error, request, reply) => {
    const known = error instanceof TrustServiceError;
    const body: ServiceErrorBody = { error: { code: known ? error.code : 'internal_error', message: known ? error.message : 'The Trust Service could not process the request.', requestId: request.id } };
    void reply.status(known ? error.statusCode : 500).send(body);
  });
  return app;
}
