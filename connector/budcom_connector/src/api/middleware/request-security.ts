import type { NextFunction, Request, Response } from 'express';

export const JSON_BODY_LIMIT = '64kb';

type BodyPresence = 'none' | 'present' | 'malformed_content_length';

function parseContentLengthHeader(value: string): number | 'malformed' {
  const trimmed = value.trim();
  if (!/^\d+$/.test(trimmed)) {
    return 'malformed';
  }
  const parsed = Number.parseInt(trimmed, 10);
  if (!Number.isFinite(parsed) || parsed < 0) {
    return 'malformed';
  }
  return parsed;
}

function hasTransferEncodedBody(req: Request): boolean {
  const transferEncoding = req.headers['transfer-encoding'];
  return typeof transferEncoding === 'string' && transferEncoding.toLowerCase().includes('chunked');
}

function hasParsedJsonBody(req: Request): boolean {
  const body = req.body;
  if (body === undefined || body === null) {
    return false;
  }
  if (typeof body === 'object') {
    if (Array.isArray(body)) {
      return body.length > 0;
    }
    return Object.keys(body as Record<string, unknown>).length > 0;
  }
  if (typeof body === 'string') {
    return body.length > 0;
  }
  return true;
}

function classifyMutationBody(req: Request): BodyPresence {
  if (hasTransferEncodedBody(req)) {
    return 'present';
  }

  const contentLength = req.headers['content-length'];
  if (contentLength !== undefined) {
    const parsed = parseContentLengthHeader(String(contentLength));
    if (parsed === 'malformed') {
      return 'malformed_content_length';
    }
    if (parsed > 0) {
      return 'present';
    }
    return 'none';
  }

  return hasParsedJsonBody(req) ? 'present' : 'none';
}

function isJsonContentType(contentType: string | undefined): boolean {
  if (!contentType) {
    return false;
  }
  const mediaType = contentType.split(';', 1)[0]?.trim().toLowerCase();
  return mediaType === 'application/json' || mediaType === 'application/json;charset=utf-8';
}

export function rejectMalformedContentLength(
  req: Request,
  res: Response,
  next: NextFunction,
): void {
  const method = req.method.toUpperCase();
  if (method === 'GET' || method === 'HEAD' || method === 'OPTIONS') {
    next();
    return;
  }

  const contentLength = req.headers['content-length'];
  if (contentLength !== undefined && parseContentLengthHeader(String(contentLength)) === 'malformed') {
    res.status(400).json({
      code: 'INVALID_CONTENT_LENGTH',
      message: 'Content-Length header is malformed.',
    });
    return;
  }

  next();
}

export function requireJsonContentTypeForMutation(
  req: Request,
  res: Response,
  next: NextFunction,
): void {
  const method = req.method.toUpperCase();
  if (method === 'GET' || method === 'HEAD' || method === 'OPTIONS') {
    next();
    return;
  }

  const bodyPresence = classifyMutationBody(req);
  if (bodyPresence === 'malformed_content_length') {
    res.status(400).json({
      code: 'INVALID_CONTENT_LENGTH',
      message: 'Content-Length header is malformed.',
    });
    return;
  }

  if (bodyPresence === 'none') {
    next();
    return;
  }

  const contentType = req.headers['content-type'];
  if (!isJsonContentType(contentType)) {
    res.status(415).json({
      code: 'UNSUPPORTED_MEDIA_TYPE',
      message: 'Content-Type must be application/json.',
    });
    return;
  }

  next();
}
