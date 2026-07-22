export const ServiceTokens = {
  Config: 'Config',
  Logger: 'Logger',
  ErpReadPort: 'ErpReadPort',
  TallyConnection: 'TallyConnection',
  SyncEngine: 'SyncEngine',
  XmlImport: 'XmlImport',
  CompanyDiscovery: 'CompanyDiscovery',
  CompanyResolver: 'CompanyResolver',
  ConnectorSession: 'ConnectorSession',
  MasterData: 'MasterData',
  TallyDiagnostics: 'TallyDiagnostics',
  LocalDatabase: 'LocalDatabase',
  ApiServer: 'ApiServer',
  Licensing: 'Licensing',
  Scheduler: 'Scheduler',
  HealthService: 'HealthService',
} as const;

export type ServiceToken = (typeof ServiceTokens)[keyof typeof ServiceTokens];
