"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.retryWithBackoff = retryWithBackoff;
const DEFAULT_SHOULD_RETRY = (error) => {
    if (error instanceof Error) {
        if (error.name === 'AbortError') {
            return true;
        }
        if (error.message.includes('fetch failed') || error.message.includes('ECONNREFUSED')) {
            return true;
        }
        if (error.message.includes('Connector request failed: HTTP 503')) {
            return true;
        }
    }
    return false;
};
function delay(ms) {
    return new Promise((resolve) => {
        setTimeout(resolve, ms);
    });
}
async function retryWithBackoff(operation, options) {
    const shouldRetry = options.shouldRetry ?? DEFAULT_SHOULD_RETRY;
    let lastError;
    for (let attempt = 1; attempt <= options.maxAttempts; attempt += 1) {
        try {
            return await operation();
        }
        catch (error) {
            lastError = error;
            if (attempt >= options.maxAttempts || !shouldRetry(error, attempt)) {
                throw error;
            }
            await delay(options.baseDelayMs * attempt);
        }
    }
    throw lastError;
}
//# sourceMappingURL=http-retry.js.map