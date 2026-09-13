/// Semantic version for API and local database schema compatibility.
class SchemaVersion implements Comparable<SchemaVersion> {
  const SchemaVersion(this.major, this.minor, this.patch);

  factory SchemaVersion.parse(String value) {
    final parts = value.split('.');
    if (parts.length != 3) {
      throw FormatException('Expected major.minor.patch, got: $value');
    }
    return SchemaVersion(
      int.parse(parts[0]),
      int.parse(parts[1]),
      int.parse(parts[2]),
    );
  }

  final int major;
  final int minor;
  final int patch;

  @override
  int compareTo(SchemaVersion other) {
    if (major != other.major) return major.compareTo(other.major);
    if (minor != other.minor) return minor.compareTo(other.minor);
    return patch.compareTo(other.patch);
  }

  bool isCompatibleWith(SchemaVersion connectorVersion) =>
      major == connectorVersion.major && compareTo(connectorVersion) <= 0;

  @override
  String toString() => '$major.$minor.$patch';
}
