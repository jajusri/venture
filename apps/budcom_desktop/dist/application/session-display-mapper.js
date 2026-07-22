"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.formatSelectionTime = formatSelectionTime;
exports.formatTimestamp = formatTimestamp;
exports.resolveCompanyName = resolveCompanyName;
exports.resolveCompanyId = resolveCompanyId;
exports.resolveErpName = resolveErpName;
exports.mapSessionDisplayStatus = mapSessionDisplayStatus;
exports.resolveSessionStatus = resolveSessionStatus;
function formatSelectionTime(selectedAt) {
    return selectedAt ?? 'Not selected';
}
function formatTimestamp(value) {
    return value ?? '—';
}
function resolveCompanyName(session) {
    return session?.session.selectedCompany?.name ?? '—';
}
function resolveCompanyId(session) {
    return session?.session.selectedCompany?.id ?? '—';
}
function resolveErpName(session) {
    const erpType = session?.session.erpType ?? 'tally';
    return erpType.charAt(0).toUpperCase() + erpType.slice(1);
}
function mapSessionDisplayStatus(connectorReachable, session, validation) {
    if (!connectorReachable) {
        return 'DISCONNECTED';
    }
    if (!session?.session.selectedCompany) {
        return 'NO_COMPANY_SELECTED';
    }
    if (session.session.connectionStatus === 'disconnected') {
        return 'DISCONNECTED';
    }
    const validationStatus = validation?.status;
    if (validationStatus === 'SUCCESS') {
        return 'ACTIVE';
    }
    if (validationStatus === 'SESSION_INVALID' ||
        validationStatus === 'SESSION_EXPIRED' ||
        validationStatus === 'COMPANY_NOT_FOUND' ||
        validationStatus === 'COMPANY_NOT_ACCESSIBLE') {
        return 'INVALID';
    }
    if (validationStatus === 'NO_COMPANY_SELECTED') {
        return 'NO_COMPANY_SELECTED';
    }
    if (session.session.connectionStatus === 'degraded') {
        return 'INVALID';
    }
    if (session.session.connectionStatus === 'connected') {
        return 'ACTIVE';
    }
    return 'ERROR';
}
/** @deprecated Use mapSessionDisplayStatus — kept for transitional tests if needed */
function resolveSessionStatus(session, validation) {
    return mapSessionDisplayStatus(true, session, validation);
}
//# sourceMappingURL=session-display-mapper.js.map