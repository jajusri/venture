export const ServiceTokens = {
  Config: 'Config',
  Logger: 'Logger',
  TallyModule: 'TallyModule',
  TallyConnection: 'TallyConnection',
  SyncEngine: 'SyncEngine',
  XmlImport: 'XmlImport',
  CompanyDiscovery: 'CompanyDiscovery',
  TallyDiagnostics: 'TallyDiagnostics',
  LocalDatabase: 'LocalDatabase',
  ApiServer: 'ApiServer',
  Licensing: 'Licensing',
  Scheduler: 'Scheduler',
  HealthService: 'HealthService',
} as const;

export type ServiceToken = (typeof ServiceTokens)[keyof typeof ServiceTokens];
