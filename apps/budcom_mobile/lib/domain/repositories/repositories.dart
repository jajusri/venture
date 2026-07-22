import 'package:budcom_core/budcom_core.dart';

abstract class CompanyRepository {
  Future<List<Company>> listCompanies();
}

abstract class LedgerRepository {
  Future<List<Ledger>> listLedgers(String companyId);
}

abstract class VoucherRepository {
  Future<List<Voucher>> listVouchers(String companyId);
}

abstract class ConnectionRepository {
  Future<ConnectionProfile> getProfile();
}

abstract class SyncRepository {
  Future<SyncCheckpoint> getCheckpoint(String companyId);
}
