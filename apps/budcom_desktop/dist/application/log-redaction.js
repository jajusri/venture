"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.redactString = redactString;
exports.redactUnknown = redactUnknown;
exports.sanitizeConfigForExport = sanitizeConfigForExport;
exports.isSensitiveKey = isSensitiveKey;
exports.stripEnvironmentVariables = stripEnvironmentVariables;
const SECRET_KEY_PATTERN = /(password|passwd|token|secret|api[_-]?key|authorization|licen[cs]e[_-]?key|credential)/i;
const BEARER_PATTERN = /bearer\s+[a-z0-9._-]+/gi;
const EMAIL_PATTERN = /\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b/gi;
const SECRET_VALUE_PATTERN = /\b(password|token|secret|api[_-]?key)\s*[:=]\s*\S+/gi;
const REDACTED = '[REDACTED]';
function redactString(value) {
    let result = value.replace(BEARER_PATTERN, `Bearer ${REDACTED}`);
    result = result.replace(EMAIL_PATTERN, REDACTED);
    result = result.replace(SECRET_VALUE_PATTERN, (match) => match.split(/[:=]/)[0] + '=[REDACTED]');
    return result;
}
function redactUnknown(value, depth = 0) {
    if (depth > 8) {
        return REDACTED;
    }
    if (typeof value === 'string') {
        return redactString(value);
    }
    if (Array.isArray(value)) {
        return value.map((item) => redactUnknown(item, depth + 1));
    }
    if (value && typeof value === 'object') {
        const output = {};
        for (const [key, nested] of Object.entries(value)) {
            if (SECRET_KEY_PATTERN.test(key)) {
                output[key] = REDACTED;
            }
            else {
                output[key] = redactUnknown(nested, depth + 1);
            }
        }
        return output;
    }
    return value;
}
function sanitizeConfigForExport(config) {
    return redactUnknown(config);
}
function isSensitiveKey(key) {
    return SECRET_KEY_PATTERN.test(key);
}
function stripEnvironmentVariables(env) {
    const allowedPrefixes = ['BUDCOM_', 'NODE_ENV', 'ELECTRON_'];
    const output = {};
    for (const [key, value] of Object.entries(env)) {
        if (!value) {
            continue;
        }
        if (SECRET_KEY_PATTERN.test(key)) {
            output[key] = REDACTED;
            continue;
        }
        if (allowedPrefixes.some((prefix) => key.startsWith(prefix))) {
            output[key] = redactString(value);
        }
    }
    return output;
}
//# sourceMappingURL=log-redaction.js.map