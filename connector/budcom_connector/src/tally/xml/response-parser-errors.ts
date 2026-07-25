export type XmlParseFailureReason =
  | 'xml_malformed'
  | 'xml_trailing_content'
  | 'xml_max_depth_exceeded'
  | 'xml_max_node_count_exceeded'
  | 'xml_invalid_parser_limit'
  | 'xml_oversized';

export class XmlParseError extends Error {
  readonly reason: XmlParseFailureReason;
  readonly details?: Readonly<Record<string, number | boolean | string>>;

  constructor(
    reason: XmlParseFailureReason,
    message: string,
    details?: Readonly<Record<string, number | boolean | string>>,
  ) {
    super(message);
    this.name = 'XmlParseError';
    this.reason = reason;
    this.details = details;
  }
}

export function toPrivacySafeXmlParseDetails(
  error: XmlParseError,
): Readonly<Record<string, number | boolean | string>> {
  return {
    reason: error.reason,
    ...(error.details ?? {}),
  };
}
