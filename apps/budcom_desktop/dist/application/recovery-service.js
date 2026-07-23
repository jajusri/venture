"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.RecoveryService = void 0;
class RecoveryService {
    logService;
    constructor(logService) {
        this.logService = logService;
    }
    handleConfigLoad(result) {
        if (result.status === 'loaded') {
            return {
                code: 'CONFIG_OK',
                state: 'Configuration loaded.',
                action: 'Continue startup.',
                result: 'No recovery required.',
            };
        }
        const action = {
            code: result.status === 'corrupt' ? 'CONFIG_CORRUPT' : result.status === 'recovered' ? 'CONFIG_RECOVERED' : 'CONFIG_DEFAULTS',
            state: result.message ?? 'Configuration issue detected.',
            action: result.status === 'corrupt' || result.status === 'recovered'
                ? 'Defaults or backup applied automatically.'
                : 'Using safe defaults.',
            result: 'Desktop can continue with validated configuration.',
        };
        this.logService.appendStructured({
            level: result.status === 'corrupt' ? 'warning' : 'information',
            message: action.state,
            event: 'config_recovery',
            component: 'recovery',
            metadata: { code: action.code },
        });
        return action;
    }
    describeFailure(code, detail) {
        const catalog = {
            PORT_IN_USE: {
                code,
                state: 'Connector port is already in use.',
                action: 'Stop the conflicting process or change the connector port in Settings.',
            },
            EXECUTABLE_MISSING: {
                code,
                state: 'Connector executable or script was not found.',
                action: 'Build the connector package or update the executable path.',
            },
            STARTUP_TIMEOUT: {
                code,
                state: 'Connector did not become ready in time.',
                action: 'Check connector logs and retry from the Connection view.',
            },
            PROCESS_CRASH: {
                code,
                state: 'Connector process stopped unexpectedly.',
                action: 'Wait for automatic restart or use Restart Connector.',
            },
            HEALTH_FAILURE: {
                code,
                state: 'Connector health endpoint is unavailable.',
                action: 'Verify connector process state and run Health Check in Diagnostics.',
            },
            TALLY_UNAVAILABLE: {
                code,
                state: 'Tally is not reachable from the connector.',
                action: 'Start Tally and confirm host/port settings.',
            },
            LOGS_UNAVAILABLE: {
                code,
                state: 'File logging location is unavailable.',
                action: 'Desktop continues with in-memory logs only.',
            },
            EXPORT_FAILURE: {
                code,
                state: 'Diagnostics export failed.',
                action: 'Retry export or copy diagnostics summary instead.',
            },
        };
        const known = catalog[code] ?? {
            code,
            state: detail ?? 'An operational issue occurred.',
            action: 'Review Diagnostics and recent logs.',
        };
        return {
            ...known,
            result: detail ?? 'No automatic recovery applied.',
        };
    }
}
exports.RecoveryService = RecoveryService;
//# sourceMappingURL=recovery-service.js.map