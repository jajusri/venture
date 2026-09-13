import 'package:venture_contracts/venture_contracts.dart';
import 'package:venture_core/venture_core.dart';
import 'package:test/test.dart';

void main() {
  test('MoneyDto round-trips debit side to domain', () {
    const dto = MoneyDto(amount: 500, currencyCode: 'INR', side: 'Dr');
    final domain = dto.toDomain();
    expect(domain.side, DebitCredit.debit);
    expect(domain.amount, 500);
    expect(MoneyDto.fromDomain(domain).side, 'Dr');
  });

  test('HealthResponse parses JSON envelope', () {
    final response = HealthResponse.fromJson({
      'status': 'ok',
      'schemaVersion': '1.0.0',
      'connectorVersion': '0.1.0',
      'tallyReachable': false,
      'readOnly': true,
    });
    expect(response.readOnly, isTrue);
    expect(response.schemaVersion, '1.0.0');
  });
}
