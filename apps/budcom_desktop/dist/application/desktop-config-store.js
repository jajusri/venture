"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.DesktopConfigStore = void 0;
const node_fs_1 = __importDefault(require("node:fs"));
const node_path_1 = __importDefault(require("node:path"));
const desktop_config_schema_js_1 = require("./desktop-config-schema.js");
class DesktopConfigStore {
    paths;
    defaults;
    fsImpl;
    current;
    constructor(options) {
        this.paths = options.paths;
        this.defaults = options.defaults;
        this.fsImpl = options.fsImpl ?? node_fs_1.default;
        this.ensureDirectories();
        const loaded = this.loadFromDisk();
        this.current = loaded.config;
    }
    getConfig() {
        return { ...this.current };
    }
    loadFromDisk() {
        this.ensureDirectories();
        if (!this.fsImpl.existsSync(this.paths.configFilePath)) {
            return {
                status: 'defaults',
                config: { ...this.defaults },
                message: 'No persisted configuration found. Using defaults.',
            };
        }
        try {
            const raw = this.fsImpl.readFileSync(this.paths.configFilePath, 'utf8');
            const parsed = JSON.parse(raw);
            const validated = (0, desktop_config_schema_js_1.validateDesktopConfig)(parsed);
            if (!validated.ok || !validated.config) {
                return this.recoverFromCorruption(validated.errors.map((error) => error.message).join('; '));
            }
            return {
                status: 'loaded',
                config: validated.config,
                message: null,
            };
        }
        catch (error) {
            const message = error instanceof Error ? error.message : String(error);
            return this.recoverFromCorruption(message);
        }
    }
    save(config) {
        const validated = (0, desktop_config_schema_js_1.validateDesktopConfig)(config);
        if (!validated.ok || !validated.config) {
            return {
                ok: false,
                message: validated.errors.map((error) => `${error.field}: ${error.message}`).join(' '),
            };
        }
        try {
            this.ensureDirectories();
            const tempPath = `${this.paths.configFilePath}.tmp`;
            const payload = `${JSON.stringify(validated.config, null, 2)}\n`;
            this.fsImpl.writeFileSync(tempPath, payload, 'utf8');
            if (this.fsImpl.existsSync(this.paths.configFilePath)) {
                this.fsImpl.copyFileSync(this.paths.configFilePath, this.paths.configBackupPath);
            }
            this.fsImpl.renameSync(tempPath, this.paths.configFilePath);
            this.current = validated.config;
            return { ok: true };
        }
        catch (error) {
            const message = error instanceof Error ? error.message : String(error);
            return { ok: false, message: `Failed to save configuration: ${message}` };
        }
    }
    restoreDefaults() {
        this.current = { ...this.defaults };
        void this.save(this.current);
        return this.getConfig();
    }
    recoverFromCorruption(reason) {
        if (this.fsImpl.existsSync(this.paths.configBackupPath)) {
            try {
                const raw = this.fsImpl.readFileSync(this.paths.configBackupPath, 'utf8');
                const parsed = JSON.parse(raw);
                const validated = (0, desktop_config_schema_js_1.validateDesktopConfig)(parsed);
                if (validated.ok && validated.config) {
                    this.fsImpl.writeFileSync(this.paths.configFilePath, `${JSON.stringify(validated.config, null, 2)}\n`, 'utf8');
                    return {
                        status: 'recovered',
                        config: validated.config,
                        message: `Recovered configuration from backup after corruption: ${reason}`,
                    };
                }
            }
            catch {
                // Fall through to defaults
            }
        }
        const corruptPath = `${this.paths.configFilePath}.corrupt-${Date.now()}.json`;
        try {
            if (this.fsImpl.existsSync(this.paths.configFilePath)) {
                this.fsImpl.copyFileSync(this.paths.configFilePath, corruptPath);
            }
        }
        catch {
            // Best effort only
        }
        this.fsImpl.writeFileSync(this.paths.configFilePath, `${JSON.stringify(this.defaults, null, 2)}\n`, 'utf8');
        return {
            status: 'corrupt',
            config: { ...this.defaults },
            message: `Configuration was corrupt (${reason}). Defaults restored.`,
        };
    }
    ensureDirectories() {
        this.fsImpl.mkdirSync(this.paths.userDataDir, { recursive: true });
        this.fsImpl.mkdirSync(this.paths.logsDir, { recursive: true });
        this.fsImpl.mkdirSync(node_path_1.default.dirname(this.paths.configFilePath), { recursive: true });
        this.fsImpl.mkdirSync(this.paths.diagnosticsExportDir, { recursive: true });
    }
}
exports.DesktopConfigStore = DesktopConfigStore;
//# sourceMappingURL=desktop-config-store.js.map