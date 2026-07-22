"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.ConnectorLifecycleService = exports.HttpHealthChecker = void 0;
const connector_http_client_js_1 = require("./connector-http-client.js");
const lifecycle_error_mapper_js_1 = require("./lifecycle-error-mapper.js");
const connector_lifecycle_config_js_1 = require("./connector-lifecycle-config.js");
class HttpHealthChecker {
    client;
    constructor(baseUrl, fetchImpl) {
        this.client = new connector_http_client_js_1.ConnectorHttpClient({
            baseUrl,
            fetchImpl,
            maxAttempts: 1,
            timeoutMs: 3_000,
        });
    }
    async checkHealth() {
        return this.client.isReachable();
    }
}
exports.HttpHealthChecker = HttpHealthChecker;
class ConnectorLifecycleService {
    config;
    processSpawner;
    healthChecker;
    logService;
    sleep;
    state = 'disconnected';
    managedByDesktop = false;
    externalProcessDetected = false;
    managedProcess = null;
    lastSuccessfulHealthCheck = null;
    lastError = null;
    restartAttempts = 0;
    processExitCode = null;
    healthTimer = null;
    reconnectTimer = null;
    startupInProgress = false;
    stopping = false;
    onStatusChanged = null;
    constructor(options) {
        this.config = options.config;
        this.processSpawner = options.processSpawner;
        this.healthChecker = options.healthChecker;
        this.logService = options.logService;
        this.sleep = options.sleep ?? ((ms) => new Promise((resolve) => setTimeout(resolve, ms)));
    }
    setStatusListener(listener) {
        this.onStatusChanged = listener;
    }
    getStatus() {
        return {
            state: this.state,
            stateLabel: (0, lifecycle_error_mapper_js_1.mapLifecycleStateLabel)(this.state),
            managedByDesktop: this.managedByDesktop,
            externalProcessDetected: this.externalProcessDetected,
            lastSuccessfulHealthCheck: this.lastSuccessfulHealthCheck,
            lastError: this.lastError,
            restartAttempts: this.restartAttempts,
            processExitCode: this.processExitCode,
            connectorExecutable: this.config.connectorExecutable,
            connectorPort: this.config.connectorPort,
            userMessage: this.lastError,
        };
    }
    async initialize() {
        this.startHealthMonitoring();
        if (this.config.autoStart) {
            await this.ensureConnectorRunning();
        }
        else {
            await this.refreshHealthState();
        }
    }
    async ensureConnectorRunning() {
        if (this.managedProcess && this.managedByDesktop) {
            if (await this.healthChecker.checkHealth()) {
                this.markHealthy();
                return this.getStatus();
            }
        }
        if (await this.healthChecker.checkHealth()) {
            if (!this.managedByDesktop) {
                this.markExternalRunning();
            }
            else {
                this.markHealthy();
            }
            return this.getStatus();
        }
        if (this.startupInProgress || this.managedProcess) {
            return this.getStatus();
        }
        return this.startManagedConnector();
    }
    async stopConnector() {
        this.clearReconnectTimer();
        this.stopping = true;
        try {
            if (!this.managedProcess) {
                if (await this.healthChecker.checkHealth()) {
                    this.lastError = (0, lifecycle_error_mapper_js_1.mapLifecycleUserMessage)('ALREADY_RUNNING');
                    this.logLifecycle('connector_stopped', 'Stop requested but connector is managed externally.');
                    this.externalProcessDetected = true;
                    this.transitionState('connected');
                    return this.getStatus();
                }
                this.transitionState('disconnected');
                return this.getStatus();
            }
            await this.managedProcess.kill('SIGTERM');
            await this.sleep(this.config.shutdownGraceMs);
            this.managedProcess = null;
            this.managedByDesktop = false;
            await this.waitForHealthDown(Math.min(this.config.startupTimeoutMs, 10_000));
            this.transitionState('disconnected');
            this.logLifecycle('connector_stopped', 'Connector stopped by desktop supervisor.');
            return this.getStatus();
        }
        catch {
            this.lastError = (0, lifecycle_error_mapper_js_1.mapLifecycleUserMessage)('STOP_FAILED');
            this.transitionState('failed');
            return this.getStatus();
        }
        finally {
            this.stopping = false;
        }
    }
    async restartConnector() {
        this.logLifecycle('connector_restarted', 'Manual connector restart requested.');
        if (this.externalProcessDetected && !this.managedByDesktop) {
            this.lastError = (0, lifecycle_error_mapper_js_1.mapLifecycleUserMessage)('ALREADY_RUNNING');
            return this.getStatus();
        }
        await this.stopConnector();
        if (await this.healthChecker.checkHealth()) {
            this.markExternalRunning();
            this.lastError = (0, lifecycle_error_mapper_js_1.mapLifecycleUserMessage)('ALREADY_RUNNING');
            return this.getStatus();
        }
        this.restartAttempts = 0;
        this.externalProcessDetected = false;
        return this.startManagedConnector();
    }
    async shutdown() {
        this.stopHealthMonitoring();
        this.clearReconnectTimer();
        if (this.managedByDesktop && this.managedProcess) {
            await this.stopConnector();
        }
    }
    startHealthMonitoring() {
        if (this.healthTimer) {
            clearInterval(this.healthTimer);
        }
        this.healthTimer = setInterval(() => {
            void this.refreshHealthState();
        }, this.config.healthPollIntervalMs);
    }
    stopHealthMonitoring() {
        if (this.healthTimer) {
            clearInterval(this.healthTimer);
            this.healthTimer = null;
        }
    }
    async startManagedConnector() {
        const validationError = (0, connector_lifecycle_config_js_1.validateConnectorExecutable)(this.config);
        if (validationError) {
            this.lastError = validationError;
            this.transitionState('failed');
            this.logLifecycle('startup_failure', validationError);
            return this.getStatus();
        }
        this.startupInProgress = true;
        this.transitionState('starting');
        this.lastError = null;
        try {
            const process = this.processSpawner.spawn({
                command: this.config.connectorExecutable,
                args: this.config.connectorArgs,
                cwd: this.config.connectorCwd,
                env: {
                    BUDCOM_CONNECTOR_PORT: String(this.config.connectorPort),
                },
            });
            this.managedProcess = process;
            this.managedByDesktop = true;
            this.externalProcessDetected = false;
            this.logLifecycle('connector_started', `Connector started with PID ${process.pid}.`);
            process.onExit((code, signal) => {
                void this.handleProcessExit(code, signal);
            });
            const ready = await this.waitForHealth(this.config.startupTimeoutMs);
            if (!ready) {
                this.lastError = (0, lifecycle_error_mapper_js_1.mapLifecycleUserMessage)('STARTUP_TIMEOUT');
                this.transitionState('failed');
                this.logLifecycle('startup_failure', this.lastError);
                return this.getStatus();
            }
            this.restartAttempts = 0;
            this.markHealthy();
            return this.getStatus();
        }
        catch (error) {
            const mapped = (0, lifecycle_error_mapper_js_1.mapSpawnError)(error);
            this.lastError = mapped.message;
            this.transitionState('failed');
            this.logLifecycle('startup_failure', mapped.message);
            return this.getStatus();
        }
        finally {
            this.startupInProgress = false;
        }
    }
    async handleProcessExit(code, signal) {
        if (this.stopping) {
            return;
        }
        this.processExitCode = code;
        this.managedProcess = null;
        this.managedByDesktop = false;
        this.logLifecycle('process_exit', `Connector process exited code=${code ?? 'null'} signal=${signal ?? 'null'}.`);
        this.logLifecycle('crash_detected', (0, lifecycle_error_mapper_js_1.mapLifecycleUserMessage)('PROCESS_CRASH'));
        if (this.restartAttempts >= this.config.maxRestartAttempts) {
            this.lastError = (0, lifecycle_error_mapper_js_1.mapLifecycleUserMessage)('MAX_RESTARTS');
            this.transitionState('failed');
            return;
        }
        this.scheduleReconnect();
    }
    scheduleReconnect() {
        this.clearReconnectTimer();
        this.restartAttempts += 1;
        const delay = this.config.reconnectBaseDelayMs * this.restartAttempts;
        this.transitionState('reconnecting');
        this.logLifecycle('restart_attempt', `Restart attempt ${this.restartAttempts} in ${delay}ms.`);
        this.logLifecycle('retry_attempt', `Retry attempt ${this.restartAttempts}.`);
        this.reconnectTimer = setTimeout(() => {
            void this.ensureConnectorRunning();
        }, delay);
    }
    async refreshHealthState() {
        if (this.startupInProgress) {
            return;
        }
        const healthy = await this.healthChecker.checkHealth();
        if (healthy) {
            this.markHealthy();
            return;
        }
        const stale = this.lastSuccessfulHealthCheck !== null &&
            Date.now() - new Date(this.lastSuccessfulHealthCheck).getTime() > this.config.staleHealthThresholdMs;
        if ((this.state === 'connected' && stale) || this.state === 'connected') {
            this.transitionState('reconnecting');
            if (this.managedByDesktop && !this.managedProcess) {
                this.scheduleReconnect();
            }
            else if (!this.managedByDesktop && this.config.autoStart) {
                this.scheduleReconnect();
            }
            else {
                this.transitionState('disconnected');
            }
            return;
        }
        if (this.state !== 'failed' && this.state !== 'reconnecting') {
            this.transitionState('disconnected');
        }
    }
    markExternalRunning() {
        this.externalProcessDetected = true;
        this.managedByDesktop = false;
        this.lastError = null;
        this.markHealthy();
        this.logLifecycle('external_process_detected', 'Existing connector process detected via /health.');
    }
    markHealthy() {
        this.lastSuccessfulHealthCheck = new Date().toISOString();
        this.lastError = null;
        this.transitionState('connected');
    }
    async waitForHealth(timeoutMs) {
        const attempts = Math.max(1, Math.ceil(timeoutMs / 500));
        for (let attempt = 0; attempt < attempts; attempt += 1) {
            if (await this.healthChecker.checkHealth()) {
                return true;
            }
            await this.sleep(500);
        }
        return false;
    }
    async waitForHealthDown(timeoutMs) {
        const attempts = Math.max(1, Math.ceil(timeoutMs / 500));
        for (let attempt = 0; attempt < attempts; attempt += 1) {
            if (!(await this.healthChecker.checkHealth())) {
                return true;
            }
            await this.sleep(500);
        }
        return false;
    }
    transitionState(next) {
        if (this.state === next) {
            return;
        }
        const previous = this.state;
        this.state = next;
        this.logLifecycle('health_transition', `${previous} -> ${next}`);
        this.notifyStatusChanged();
    }
    logLifecycle(event, message) {
        const level = event === 'startup_failure' || event === 'crash_detected' ? 'error' : 'information';
        if (event === 'retry_attempt' || event === 'restart_attempt') {
            this.logService.append('warning', `[lifecycle:${event}] ${message}`);
            return;
        }
        this.logService.append(level, `[lifecycle:${event}] ${message}`);
    }
    clearReconnectTimer() {
        if (this.reconnectTimer) {
            clearTimeout(this.reconnectTimer);
            this.reconnectTimer = null;
        }
    }
    notifyStatusChanged() {
        this.onStatusChanged?.();
    }
}
exports.ConnectorLifecycleService = ConnectorLifecycleService;
//# sourceMappingURL=connector-lifecycle-service.js.map