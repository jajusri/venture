import { readFileSync, writeFileSync } from 'node:fs';
import {
  pilotBindingSigningPayload,
  credentialClaimsSigningPayload,
  authenticatedRequestSigningPayload,
  type PilotCredentialClaimsWire,
} from '../services/relay/src/devtools/pilot-envelope.js';

const path = new URL('../../shared/fixtures/relay/authenticated-envelope-golden-v1.json', import.meta.url);
const fixture = JSON.parse(readFileSync(path, 'utf8'));

const claims: PilotCredentialClaimsWire = fixture.credentialClaims;
fixture.credentialClaimsSigningBytesBase64 = Buffer.from(credentialClaimsSigningPayload(claims)).toString('base64');
fixture.submitBindingSigningBytesBase64 = Buffer.from(pilotBindingSigningPayload(fixture.submitBinding)).toString('base64');
fixture.fetchBindingSigningBytesBase64 = Buffer.from(authenticatedRequestSigningPayload(fixture.fetchBinding)).toString('base64');
fixture.ackBindingSigningBytesBase64 = Buffer.from(authenticatedRequestSigningPayload(fixture.ackBinding)).toString('base64');

writeFileSync(path, JSON.stringify(fixture, null, 2) + '\n', 'utf8');
console.log('Regenerated golden fixture signing bytes.');
