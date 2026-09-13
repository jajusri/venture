# VENTURE central backend

This directory contains the separately deployable VENTURE central services. It is not part of the LAN/Tally Connector.

Only the Trust Service is currently implemented. No relay or public deployment is included.

## Trust Service

Requires Node.js 24 LTS. Configuration is read through `TrustServiceConfig`; the HTTP listener defaults to loopback for local development.

```powershell
npm install
npm test
npm run typecheck
npm run dev
```
