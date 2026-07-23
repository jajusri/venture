"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.LogService = void 0;
const log_redaction_js_1 = require("./log-redaction.js");
let sequence = 0;
const LOG_LEVEL_ORDER = {
    debug: 10,
    info: 20,
    warn: 30,
    error: 40,
};
function mapDesktopLevel(level) {
    switch (level) {
        case 'warning':
            return 'warn';
        case 'error':
            return 'error';
        default:
            return 'info';
    }
}
function sanitizeMetadata(metadata) {
    const output = {};
    for (const [key, value] of Object.entries(metadata)) {
        if (value !== undefined) {
            output[key] = value;
        }
    }
    return output;
}
class LogService {
    entries = [];
    maxEntries;
    fileWriter;
    minimumLevel;
    consoleEnabled;
    constructor(options) {
        this.maxEntries = options?.maxEntries ?? 500;
        this.fileWriter = options?.fileWriter ?? null;
        this.minimumLevel = options?.minimumLevel ?? 'info';
        this.consoleEnabled = options?.consoleEnabled ?? process.env.NODE_ENV !== 'production';
    }
    setMinimumLevel(level) {
        this.minimumLevel = level;
    }
    append(level, message, timestamp = new Date().toISOString()) {
        return this.appendStructured({
            level,
            message,
            timestamp,
        });
    }
    appendStructured(input) {
        const sanitizedMessage = (0, log_redaction_js_1.redactString)(input.message);
        const desktopLevel = mapDesktopLevel(input.level);
        const metadata = input.metadata ? sanitizeMetadata(input.metadata) : null;
        if (LOG_LEVEL_ORDER[desktopLevel] < LOG_LEVEL_ORDER[this.minimumLevel]) {
            return {
                id: `log-${++sequence}`,
                timestamp: input.timestamp ?? new Date().toISOString(),
                level: input.level,
                message: sanitizedMessage,
                event: input.event ?? null,
                component: input.component ?? null,
                metadata,
            };
        }
        const entry = {
            id: `log-${++sequence}`,
            timestamp: input.timestamp ?? new Date().toISOString(),
            level: input.level,
            message: sanitizedMessage,
            event: input.event ?? null,
            component: input.component ?? null,
            metadata,
        };
        this.entries.unshift(entry);
        if (this.entries.length > this.maxEntries) {
            this.entries.length = this.maxEntries;
        }
        const filePayload = JSON.stringify({
            timestamp: entry.timestamp,
            level: mapDesktopLevel(entry.level),
            event: entry.event,
            component: entry.component,
            message: entry.message,
            metadata: entry.metadata,
        });
        this.fileWriter?.appendLine(filePayload);
        if (this.consoleEnabled) {
            const prefix = `[${entry.component ?? 'desktop'}:${entry.event ?? 'log'}]`;
            console.error(prefix, entry.message);
        }
        return entry;
    }
    getEntries() {
        return [...this.entries];
    }
    getLifecycleEvents(limit = 50) {
        return this.entries.filter((entry) => entry.message.includes('[lifecycle:') || entry.event?.startsWith('lifecycle')).slice(0, limit);
    }
    getRecentErrors(limit = 25) {
        return this.entries.filter((entry) => entry.level === 'error' || entry.level === 'warning').slice(0, limit);
    }
    clear() {
        this.entries.length = 0;
    }
    clearNonessential() {
        this.entries.splice(0, this.entries.length, ...this.entries.filter((entry) => entry.level === 'error'));
    }
    getLogFilePath() {
        return this.fileWriter?.getLogFilePath() ?? null;
    }
    isFileLoggingAvailable() {
        return this.fileWriter?.isWritable() ?? false;
    }
    getFileLoggingDisabledReason() {
        return this.fileWriter?.getDisabledReason() ?? null;
    }
}
exports.LogService = LogService;
//# sourceMappingURL=log-service.js.map