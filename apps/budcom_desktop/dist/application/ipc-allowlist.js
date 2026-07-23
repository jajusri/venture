"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.ALLOWED_PUSH_CHANNELS = exports.ALLOWED_IPC_CHANNELS = void 0;
exports.isAllowedIpcChannel = isAllowedIpcChannel;
exports.assertAllowedIpcChannel = assertAllowedIpcChannel;
exports.validateCompanyId = validateCompanyId;
exports.validateSettingsInput = validateSettingsInput;
exports.validateExportDirectory = validateExportDirectory;
exports.ALLOWED_IPC_CHANNELS = [
    'desktop:get-dashboard',
    'desktop:get-logs',
    'desktop:get-settings',
    'desktop:save-settings',
    'desktop:restore-default-settings',
    'desktop:validate-settings',
    'desktop:get-lifecycle-status',
    'desktop:start-connector',
    'desktop:stop-connector',
    'desktop:restart-connector',
    'desktop:get-companies',
    'desktop:select-company',
    'desktop:clear-company',
    'desktop:get-diagnostics',
    'desktop:refresh-diagnostics',
    'desktop:copy-diagnostics-summary',
    'desktop:export-diagnostics-bundle',
    'desktop:open-logs-folder',
    'desktop:clear-nonessential-logs',
    'desktop:run-health-check',
    'desktop:reload-renderer',
];
exports.ALLOWED_PUSH_CHANNELS = ['desktop:status-updated'];
function isAllowedIpcChannel(channel) {
    return exports.ALLOWED_IPC_CHANNELS.includes(channel);
}
function assertAllowedIpcChannel(channel) {
    if (!isAllowedIpcChannel(channel)) {
        throw new Error(`Blocked IPC channel: ${channel}`);
    }
}
function validateCompanyId(value) {
    if (typeof value !== 'string') {
        throw new Error('Company id must be a string.');
    }
    const trimmed = value.trim();
    if (!/^[a-zA-Z0-9._-]{1,128}$/.test(trimmed)) {
        throw new Error('Company id contains invalid characters.');
    }
    return trimmed;
}
function validateSettingsInput(value) {
    if (!value || typeof value !== 'object' || Array.isArray(value)) {
        throw new Error('Settings payload must be an object.');
    }
    return value;
}
function validateExportDirectory(value) {
    if (value === undefined || value === null) {
        return undefined;
    }
    if (typeof value !== 'string' || value.trim().length === 0) {
        throw new Error('Export directory must be a non-empty string.');
    }
    return value.trim();
}
//# sourceMappingURL=ipc-allowlist.js.map