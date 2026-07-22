"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.formatSelectionTime = formatSelectionTime;
exports.resolveSessionStatus = resolveSessionStatus;
exports.resolveCompanyName = resolveCompanyName;
exports.resolveCompanyId = resolveCompanyId;
function formatSelectionTime(selectedAt) {
    return selectedAt ?? 'Not selected';
}
function resolveSessionStatus(session, validation) {
    if (validation?.status) {
        return validation.status;
    }
    if (!session?.session.selectedCompany) {
        return 'NO_COMPANY_SELECTED';
    }
    return session.session.connectionStatus.toUpperCase();
}
function resolveCompanyName(session) {
    return session?.session.selectedCompany?.name ?? '—';
}
function resolveCompanyId(session) {
    return session?.session.selectedCompany?.id ?? '—';
}
//# sourceMappingURL=session-display-mapper.js.map