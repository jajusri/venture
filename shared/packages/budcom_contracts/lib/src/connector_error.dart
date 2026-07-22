/// Standard connector error envelope.
class ConnectorError {
  const ConnectorError({
    required this.code,
    required this.message,
    this.details,
  });

  final String code;
  final String message;
  final Map<String, dynamic>? details;

  factory ConnectorError.fromJson(Map<String, dynamic> json) {
    return ConnectorError(
      code: json['code'] as String,
      message: json['message'] as String,
      details: json['details'] as Map<String, dynamic>?,
    );
  }

  Map<String, dynamic> toJson() => {
        'code': code,
        'message': message,
        if (details != null) 'details': details,
      };
}
