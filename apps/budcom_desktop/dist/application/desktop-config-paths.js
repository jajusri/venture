"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.resolveDesktopConfigPaths = resolveDesktopConfigPaths;
const node_path_1 = __importDefault(require("node:path"));
function resolveDesktopConfigPaths(userDataDir) {
    return {
        userDataDir,
        configFilePath: node_path_1.default.join(userDataDir, 'desktop-config.json'),
        configBackupPath: node_path_1.default.join(userDataDir, 'desktop-config.backup.json'),
        logsDir: node_path_1.default.join(userDataDir, 'logs'),
        diagnosticsExportDir: node_path_1.default.join(userDataDir, 'diagnostics-exports'),
    };
}
//# sourceMappingURL=desktop-config-paths.js.map