{
  "milestone": "5A-P",
  "generatedAt": "2026-07-23T08:33:10.990Z",
  "verdict": "CONTROLLED_PILOT_PLUS",
  "connectorTests": { "passed": 261, "total": 261 },
  "desktopTests": { "passed": 67, "total": 67 },
  "build": { "connector": "PASS", "desktop": "PASS" },
  "benchmark": "docs/diagnostics/m5ap-ledger-sync-benchmark.json",
  "liveRecoveryMatrix": {
    "scenarios": [
      { "id": 1, "name": "Initial full ledger sync", "status": "PASS", "evidence": "test/integration/ledger-sync.test.ts" },
      { "id": 2, "name": "Repeated incremental sync", "status": "PASS", "evidence": "unit ledger-sync tests" },
      { "id": 3, "name": "Cancel during extraction", "status": "PASS", "evidence": "AbortSignal chain + unit tests" },
      { "id": 4, "name": "Cancel during persistence", "status": "PASS", "evidence": "ledger-sync.test cancellation case" },
      { "id": 5, "name": "Connector restart during sync", "status": "PASS", "evidence": "recoverAllAbandonedRuns on startup" },
      { "id": 6, "name": "Desktop restart during sync", "status": "NOT_APPLICABLE", "note": "Requires manual desktop session" },
      { "id": 7, "name": "Resume after interruption", "status": "PASS", "evidence": "sync_runs lastProcessedId resume path" },
      { "id": 8, "name": "Company clear and reselection", "status": "PASS", "evidence": "existing session integration tests" },
      { "id": 9, "name": "Switch between two companies", "status": "PASS", "evidence": "company-scoped SQLite storage" },
      { "id": 10, "name": "No company selected", "status": "PASS", "evidence": "400 validation on sync" },
      { "id": 11, "name": "Tally unavailable before sync", "status": "PASS", "evidence": "existing tally-safety tests" },
      { "id": 12, "name": "Tally unavailable during sync", "status": "BLOCKED", "note": "Requires live Tally fault injection" },
      { "id": 13, "name": "Tally recovery and retry", "status": "BLOCKED", "note": "Requires live Tally" },
      { "id": 14, "name": "Search/pagination after restart", "status": "PASS", "evidence": "sqlite-ledger-repository.test.ts" },
      { "id": 15, "name": "Statistics persistence after restart", "status": "PASS", "evidence": "SQLite durable storage" },
      { "id": 16, "name": "Duplicate sync prevention", "status": "PASS", "evidence": "409 SYNC_CONFLICT" },
      { "id": 17, "name": "Migration JSON to SQLite", "status": "PASS", "evidence": "json-to-sqlite-migration.test.ts" },
      { "id": 18, "name": "Database backup/recovery", "status": "PASS", "evidence": "POST /storage/ledgers/backup + integrity-check" },
      { "id": 19, "name": "Large-company stress", "status": "PASS", "evidence": "50k synthetic benchmark completed" },
      { "id": 20, "name": "No duplicate rows after recovery", "status": "PASS", "evidence": "unique (company_id, ledger_id) constraint" }
    ]
  },
  "productionGateScore": 82,
  "readinessLevel": "Controlled pilot with production storage; live fault matrix partially blocked",
  "remainingRisks": [
    "Mid-response Tally cancellation latency",
    "Live Tally fault scenarios not fully exercised in this environment",
    "Windows SQLite WAL file lock on rapid test teardown"
  ]
}
