"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.FileLogWriter = void 0;
const node_fs_1 = __importDefault(require("node:fs"));
const node_path_1 = __importDefault(require("node:path"));
class FileLogWriter {
    logsDir;
    fileName;
    maxFileBytes;
    maxFiles;
    fsImpl;
    writeDisabled = false;
    disabledReason = null;
    constructor(options) {
        this.logsDir = options.logsDir;
        this.fileName = options.fileName ?? 'budcom-desktop.log';
        this.maxFileBytes = options.maxFileBytes ?? 1_048_576;
        this.maxFiles = options.maxFiles ?? 5;
        this.fsImpl = options.fsImpl ?? node_fs_1.default;
        this.ensureLogsDir();
    }
    getLogFilePath() {
        return node_path_1.default.join(this.logsDir, this.fileName);
    }
    isWritable() {
        return !this.writeDisabled;
    }
    getDisabledReason() {
        return this.disabledReason;
    }
    appendLine(line) {
        if (this.writeDisabled) {
            return;
        }
        try {
            this.ensureLogsDir();
            this.rotateIfNeeded();
            this.fsImpl.appendFileSync(this.getLogFilePath(), `${line}\n`, 'utf8');
        }
        catch (error) {
            this.writeDisabled = true;
            this.disabledReason = error instanceof Error ? error.message : String(error);
        }
    }
    clearCurrentLog() {
        if (this.writeDisabled) {
            return;
        }
        try {
            this.ensureLogsDir();
            this.fsImpl.writeFileSync(this.getLogFilePath(), '', 'utf8');
        }
        catch (error) {
            this.writeDisabled = true;
            this.disabledReason = error instanceof Error ? error.message : String(error);
        }
    }
    ensureLogsDir() {
        this.fsImpl.mkdirSync(this.logsDir, { recursive: true });
    }
    rotateIfNeeded() {
        const currentPath = this.getLogFilePath();
        if (!this.fsImpl.existsSync(currentPath)) {
            return;
        }
        const stats = this.fsImpl.statSync(currentPath);
        if (stats.size < this.maxFileBytes) {
            return;
        }
        for (let index = this.maxFiles - 1; index >= 1; index -= 1) {
            const from = `${currentPath}.${index}`;
            const to = `${currentPath}.${index + 1}`;
            if (this.fsImpl.existsSync(from)) {
                if (index + 1 > this.maxFiles) {
                    this.fsImpl.unlinkSync(from);
                }
                else {
                    this.fsImpl.renameSync(from, to);
                }
            }
        }
        this.fsImpl.renameSync(currentPath, `${currentPath}.1`);
        this.fsImpl.writeFileSync(currentPath, '', 'utf8');
    }
}
exports.FileLogWriter = FileLogWriter;
//# sourceMappingURL=file-log-writer.js.map