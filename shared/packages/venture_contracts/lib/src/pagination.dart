/// Cursor-based pagination metadata for large lists.
class PaginationMeta {
  const PaginationMeta({
    required this.page,
    required this.pageSize,
    required this.totalItems,
    required this.hasMore,
  });

  final int page;
  final int pageSize;
  final int totalItems;
  final bool hasMore;

  factory PaginationMeta.fromJson(Map<String, dynamic> json) {
    return PaginationMeta(
      page: json['page'] as int,
      pageSize: json['pageSize'] as int,
      totalItems: json['totalItems'] as int,
      hasMore: json['hasMore'] as bool,
    );
  }

  Map<String, dynamic> toJson() => {
        'page': page,
        'pageSize': pageSize,
        'totalItems': totalItems,
        'hasMore': hasMore,
      };
}

class PaginatedResponse<T> {
  const PaginatedResponse({
    required this.items,
    required this.pagination,
    required this.schemaVersion,
    required this.dataFreshnessAt,
  });

  final List<T> items;
  final PaginationMeta pagination;
  final String schemaVersion;
  final DateTime dataFreshnessAt;
}
