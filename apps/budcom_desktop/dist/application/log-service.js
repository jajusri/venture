"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.LogService = void 0;
let sequence = 0;
class LogService {
    entries = [];
    maxEntries;
    constructor(maxEntries = 500) {
        this.maxEntries = maxEntries;
    }
    append(level, message, timestamp = new Date().toISOString()) {
        const entry = {
            id: `log-${++sequence}`,
            timestamp,
            level,
            message,
        };
        this.entries.unshift(entry);
        if (this.entries.length > this.maxEntries) {
            this.entries.length = this.maxEntries;
        }
        return entry;
    }
    getEntries() {
        return [...this.entries];
    }
    clear() {
        this.entries.length = 0;
    }
}
exports.LogService = LogService;
//# sourceMappingURL=log-service.js.map