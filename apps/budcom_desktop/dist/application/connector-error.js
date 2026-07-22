"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.ConnectorRequestError = void 0;
exports.toUserMessage = toUserMessage;
exports.mapConnectorErrorBody = mapConnectorErrorBody;
exports.mapSelectionUserMessage = mapSelectionUserMessage;
exports.mapDiscoveryUserMessage = mapDiscoveryUserMessage;
class ConnectorRequestError extends Error {
    statusCode;
    code;
    userMessage;
    details;
    constructor(statusCode, code, userMessage, details) {
        super(userMessage);
        this.name = 'ConnectorRequestError';
        this.statusCode = statusCode;
        this.code = code;
        this.userMessage = userMessage;
        this.details = details;
    }
}
exports.ConnectorRequestError = ConnectorRequestError;
function toUserMessage(error) {
    if (error instanceof ConnectorRequestError) {
        return error.userMessage;
    }
    if (error instanceof Error) {
        if (error.name === 'AbortError') {
            return 'The connector did not respond in time. Please check that it is running.';
        }
        if (error.message.includes('fetch failed') || error.message.includes('ECONNREFUSED')) {
            return 'Cannot reach the connector service. Ensure it is running on the configured URL.';
        }
        return 'An unexpected error occurred while contacting the connector.';
    }
    return 'An unexpected error occurred.';
}
function mapConnectorErrorBody(statusCode, body) {
    const code = body.code ?? 'REQUEST_FAILED';
    const message = body.message ?? `Connector request failed with HTTP ${statusCode}`;
    return new ConnectorRequestError(statusCode, code, message, body.details);
}
const SELECTION_MESSAGES = {
    SUCCESS: 'Company selected successfully.',
    DUPLICATE_SELECTION: 'This company is already selected.',
    EMPTY_SELECTION: 'Please choose a company before continuing.',
    INVALID_COMPANY: 'The connector cannot select a company right now. Check Tally connection.',
    COMPANY_NOT_FOUND: 'That company was not found. Refresh the company list and try again.',
    CONNECTOR_UNAVAILABLE: 'Cannot reach the connector service.',
};
function mapSelectionUserMessage(status, reason) {
    if (reason && status !== 'SUCCESS' && status !== 'DUPLICATE_SELECTION') {
        return reason;
    }
    return SELECTION_MESSAGES[status];
}
function mapDiscoveryUserMessage(status, reason) {
    if (reason) {
        return reason;
    }
    switch (status) {
        case 'EMPTY':
            return 'No companies were found in Tally.';
        case 'UNAVAILABLE':
        case 'TIMEOUT':
            return 'Tally is unavailable for company discovery.';
        case 'DENIED':
            return 'Company discovery is not permitted by connector policy.';
        case 'MALFORMED':
            return 'Company discovery returned unexpected data.';
        default:
            return 'Unable to load companies from the connector.';
    }
}
//# sourceMappingURL=connector-error.js.map