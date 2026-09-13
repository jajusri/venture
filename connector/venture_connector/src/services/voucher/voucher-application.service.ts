import type { VoucherDetails, VoucherSearchCriteria, VoucherSummaryRecord } from '../../erp/voucher/voucher-domain.js';
import type { VoucherApplicationService } from './voucher-application.interface.js';
import type {
  VoucherDetailsDto,
  VoucherDetailsQueryDto,
  VoucherListDto,
  VoucherListQueryDto,
  VoucherPublicDetails,
  VoucherPublicRecord,
  VoucherSnapshotDetailsDto,
  VoucherSnapshotDto,
  VoucherSnapshotListDto,
  VoucherSummaryDto,
  VoucherSummaryQueryDto,
} from './voucher-dtos.js';
import type {
  VoucherRepositoryPort,
  VoucherSnapshotMetadata,
} from './voucher-repository.interface.js';

export class VoucherApplicationServiceImpl implements VoucherApplicationService {
  constructor(private readonly getRepository: () => VoucherRepositoryPort) {}

  async getSummary(query: VoucherSummaryQueryDto): Promise<VoucherSummaryDto> {
    const statistics = await this.getRepository().getStatistics(query.companyId, query.period);
    return {
      companyId: query.companyId,
      statistics: {
        dateFrom: statistics.dateFrom,
        dateTo: statistics.dateTo,
        totalVouchers: statistics.totalVouchers,
        countsByType: statistics.countsByType.map((entry) => ({ ...entry })),
        lastSynchronizedAt: statistics.lastSynchronizedAt,
      },
    };
  }

  async list(query: VoucherListQueryDto): Promise<VoucherListDto> {
    // TD-023: search() is already SQL-paginated (LIMIT/OFFSET against the active snapshot,
    // filtered/sorted in SQL) — previously this method loaded the entire company snapshot via
    // querySnapshot() and filtered/sorted/paginated it in application memory on every single
    // request, which scaled with total accumulated history rather than the requested page.
    const criteria: VoucherSearchCriteria = {
      ...query.criteria,
      ...(query.voucherNumber ? { voucherNumber: query.voucherNumber } : {}),
      ...(query.partyName ? { partyName: query.partyName } : {}),
    };
    const result = await this.getRepository().search(query.companyId, criteria);
    const mapItem = query.includeDetails ? toPublicDetails : toPublicRecord;
    return {
      companyId: query.companyId,
      items: result.items.map(mapItem),
      pagination: result.pagination,
    };
  }

  async getDetails(query: VoucherDetailsQueryDto): Promise<VoucherDetailsDto> {
    const voucher = await this.getRepository().getVoucher(query.companyId, query.voucherId);
    return {
      companyId: query.companyId,
      voucher: voucher ? toPublicDetails(voucher) : null,
    };
  }

  async listSnapshots(companyId: string): Promise<VoucherSnapshotListDto> {
    const snapshots = await this.getRepository().listSnapshots(companyId);
    return { companyId, snapshots: snapshots.map(toSnapshotDto) };
  }

  async getSnapshot(
    companyId: string,
    snapshotId: string,
  ): Promise<VoucherSnapshotDetailsDto> {
    const snapshot = await this.getRepository().getSnapshot(companyId, snapshotId);
    return { companyId, snapshot: snapshot ? toSnapshotDto(snapshot) : null };
  }
}

function toPublicRecord(voucher: VoucherSummaryRecord): VoucherPublicRecord {
  return {
    id: voucher.voucherId,
    date: voucher.date,
    type: voucher.voucherType,
    number: voucher.voucherNumber,
    partyName: voucher.partyName,
    referenceNumber: voucher.referenceNumber ?? null,
    amount: voucher.amount
      ? { value: voucher.amount.amount, side: voucher.amount.side }
      : null,
    status: voucher.status,
    dataQuality: voucher.dataQuality,
  };
}

function toPublicDetails(voucher: VoucherDetails): VoucherPublicDetails {
  return {
    ...toPublicRecord(voucher),
    effectiveDate: voucher.effectiveDate ?? null,
    narration: voucher.narration ?? null,
    ledgerEntries: voucher.ledgerEntries.map((entry) => ({
      lineNumber: entry.lineNumber,
      ledgerName: entry.ledgerName,
      amount: { value: entry.amount.amount, side: entry.amount.side },
      isDeemedPositive: entry.isDeemedPositive,
    })),
    inventoryEntries: voucher.inventoryEntries.map((entry) => ({
      lineNumber: entry.lineNumber,
      itemName: entry.itemName,
      quantity: entry.quantity ?? null,
      rate: entry.rate ?? null,
      amount: entry.amount
        ? { value: entry.amount.amount, side: entry.amount.side }
        : null,
    })),
  };
}

function toSnapshotDto(snapshot: VoucherSnapshotMetadata): VoucherSnapshotDto {
  return {
    id: snapshot.snapshotId,
    from: snapshot.period.dateFrom,
    to: snapshot.period.dateTo,
    status: snapshot.status,
    createdAt: snapshot.createdAt ?? null,
    validatedAt: snapshot.validatedAt ?? null,
    promotedAt: snapshot.promotedAt ?? null,
    voucherCount: snapshot.voucherCount ?? 0,
  };
}
