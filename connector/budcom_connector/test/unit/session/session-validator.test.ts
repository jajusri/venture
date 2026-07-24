import { describe, expect, it } from 'vitest';

import { ERP_TYPE_TALLY } from '../../../src/erp/session/session-constants.js';
import {
  createEmptySession,
  selectCompany,
  validateSession,
  withSelectedCompany,
} from '../../../src/services/session/session-validator.js';

const BASE_COMPANIES = [
  { id: 'estimation', name: 'ESTIMATION' },
  { id: 'learn', name: 'LEARN' },
] as const;

describe('session validator', () => {
  it('rejects empty company selection', () => {
    const session = createEmptySession({
      connectorVersion: '0.3.1',
      erpType: ERP_TYPE_TALLY,
      connectionStatus: 'connected',
      nowMs: Date.now(),
    });

    const result = selectCompany({
      session,
      companyId: '   ',
      discoveredCompanies: BASE_COMPANIES,
      tallyReachable: true,
      discoveryAvailable: true,
      connectorConnected: true,
      nowMs: Date.now(),
    });

    expect(result.status).toBe('EMPTY_SELECTION');
  });

  it('rejects unknown company selection', () => {
    const session = createEmptySession({
      connectorVersion: '0.3.1',
      erpType: ERP_TYPE_TALLY,
      connectionStatus: 'connected',
      nowMs: Date.now(),
    });

    const result = selectCompany({
      session,
      companyId: 'missing-co',
      discoveredCompanies: BASE_COMPANIES,
      tallyReachable: true,
      discoveryAvailable: true,
      connectorConnected: true,
      nowMs: Date.now(),
    });

    expect(result.status).toBe('COMPANY_NOT_FOUND');
  });

  it('rejects duplicate selection', () => {
    const nowMs = Date.now();
    const session = withSelectedCompany(
      createEmptySession({
        connectorVersion: '0.3.1',
        erpType: ERP_TYPE_TALLY,
        connectionStatus: 'connected',
        nowMs,
      }),
      { id: 'estimation', name: 'ESTIMATION' },
      nowMs,
    );

    const result = selectCompany({
      session,
      companyId: 'estimation',
      discoveredCompanies: BASE_COMPANIES,
      tallyReachable: true,
      discoveryAvailable: true,
      connectorConnected: true,
      nowMs,
    });

    expect(result.status).toBe('DUPLICATE_SELECTION');
  });

  it('selects a valid company', () => {
    const session = createEmptySession({
      connectorVersion: '0.3.1',
      erpType: ERP_TYPE_TALLY,
      connectionStatus: 'connected',
      nowMs: Date.now(),
    });

    const result = selectCompany({
      session,
      companyId: 'estimation',
      discoveredCompanies: BASE_COMPANIES,
      tallyReachable: true,
      discoveryAvailable: true,
      connectorConnected: true,
      nowMs: Date.now(),
    });

    expect(result.status).toBe('SUCCESS');
    expect(result.session.selectedCompany).toEqual({
      id: 'estimation',
      name: 'ESTIMATION',
    });
  });

  it('returns NO_COMPANY_SELECTED when session has no company', () => {
    const session = createEmptySession({
      connectorVersion: '0.3.1',
      erpType: ERP_TYPE_TALLY,
      connectionStatus: 'connected',
      nowMs: Date.now(),
    });

    const result = validateSession({
      session,
      discoveredCompanies: BASE_COMPANIES,
      tallyReachable: true,
      discoveryAvailable: true,
      connectorConnected: true,
      sessionTtlMs: 60_000,
      nowMs: Date.now(),
    });

    expect(result.status).toBe('NO_COMPANY_SELECTED');
  });

  it('returns COMPANY_NOT_ACCESSIBLE when requested company mismatches selection', () => {
    const nowMs = Date.now();
    const session = withSelectedCompany(
      createEmptySession({
        connectorVersion: '0.3.1',
        erpType: ERP_TYPE_TALLY,
        connectionStatus: 'connected',
        nowMs,
      }),
      { id: 'estimation', name: 'ESTIMATION' },
      nowMs,
    );

    const result = validateSession({
      session,
      requestedCompanyId: 'learn',
      discoveredCompanies: BASE_COMPANIES,
      tallyReachable: true,
      discoveryAvailable: true,
      connectorConnected: true,
      sessionTtlMs: 60_000,
      nowMs,
    });

    expect(result.status).toBe('COMPANY_NOT_ACCESSIBLE');
  });

  it('returns SESSION_EXPIRED for stale sessions', () => {
    const selectedAtMs = Date.parse('2026-01-01T00:00:00.000Z');
    const session = withSelectedCompany(
      createEmptySession({
        connectorVersion: '0.3.1',
        erpType: ERP_TYPE_TALLY,
        connectionStatus: 'connected',
        nowMs: selectedAtMs,
      }),
      { id: 'estimation', name: 'ESTIMATION' },
      selectedAtMs,
    );

    const result = validateSession({
      session,
      discoveredCompanies: BASE_COMPANIES,
      tallyReachable: true,
      discoveryAvailable: true,
      connectorConnected: true,
      sessionTtlMs: 1_000,
      nowMs: selectedAtMs + 5_000,
    });

    expect(result.status).toBe('SESSION_EXPIRED');
  });

  it('returns COMPANY_NOT_FOUND when selected company disappears from discovery', () => {
    const nowMs = Date.now();
    const session = withSelectedCompany(
      createEmptySession({
        connectorVersion: '0.3.1',
        erpType: ERP_TYPE_TALLY,
        connectionStatus: 'connected',
        nowMs,
      }),
      { id: 'estimation', name: 'ESTIMATION' },
      nowMs,
    );

    const result = validateSession({
      session,
      discoveredCompanies: [],
      tallyReachable: true,
      discoveryAvailable: true,
      connectorConnected: true,
      sessionTtlMs: 60_000,
      nowMs,
    });

    expect(result.status).toBe('COMPANY_NOT_FOUND');
  });

  it('returns SUCCESS and refreshes validation timestamp', () => {
    const nowMs = Date.now();
    const session = withSelectedCompany(
      createEmptySession({
        connectorVersion: '0.3.1',
        erpType: ERP_TYPE_TALLY,
        connectionStatus: 'connected',
        nowMs,
      }),
      { id: 'estimation', name: 'ESTIMATION' },
      nowMs,
    );

    const result = validateSession({
      session,
      requestedCompanyId: 'estimation',
      discoveredCompanies: BASE_COMPANIES,
      tallyReachable: true,
      discoveryAvailable: true,
      connectorConnected: true,
      sessionTtlMs: 60_000,
      nowMs: nowMs + 1_000,
    });

    expect(result.status).toBe('SUCCESS');
    expect(result.session.lastValidatedAt).not.toBe(session.lastValidatedAt);
  });

  it('returns COMPANY_DISCOVERY_UNAVAILABLE when discovery is unavailable with selected company', () => {
    const nowMs = Date.now();
    const session = withSelectedCompany(
      createEmptySession({
        connectorVersion: '0.3.1',
        erpType: ERP_TYPE_TALLY,
        connectionStatus: 'connected',
        nowMs,
      }),
      { id: 'estimation', name: 'ESTIMATION' },
      nowMs,
    );

    const result = validateSession({
      session,
      discoveredCompanies: [],
      tallyReachable: true,
      discoveryAvailable: false,
      connectorConnected: true,
      sessionTtlMs: 60_000,
      nowMs,
    });

    expect(result.status).toBe('COMPANY_DISCOVERY_UNAVAILABLE');
    expect(result.reason).toContain('reachability alone');
  });

  it('rejects company selection when discovery is unavailable even if Tally is reachable', () => {
    const session = createEmptySession({
      connectorVersion: '0.3.1',
      erpType: ERP_TYPE_TALLY,
      connectionStatus: 'connected',
      nowMs: Date.now(),
    });

    const result = selectCompany({
      session,
      companyId: 'estimation',
      discoveredCompanies: BASE_COMPANIES,
      tallyReachable: true,
      discoveryAvailable: false,
      connectorConnected: true,
      nowMs: Date.now(),
    });

    expect(result.status).toBe('INVALID_COMPANY');
    expect(result.reason).toContain('Company discovery is unavailable');
  });
});
