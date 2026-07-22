export const ServiceTokens = {
  Config: 'Config',
  Logger: 'Logger',
  TallyConnection: 'TallyConnection',
  SyncEngine: 'SyncEngine',
  XmlImport: 'XmlImport',
  LocalDatabase: 'LocalDatabase',
  ApiServer: 'ApiServer',
  Licensing: 'Licensing',
  Scheduler: 'Scheduler',
  HealthService: 'HealthService',
} as const;

export type ServiceToken = (typeof ServiceTokens)[keyof typeof ServiceTokens];
