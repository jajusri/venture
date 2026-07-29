import type { VoucherDetails, VoucherSummaryRecord } from '../../erp/voucher/voucher-domain.js';
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
    const vouchers = await this.getRepository().querySnapshot(query.companyId);
    const filtered = vouchers.filter((voucher) =>
      voucher.date >= query.criteria.dateFrom &&
      voucher.date <= query.criteria.dateTo &&
      (!query.criteria.voucherType || voucher.voucherType === query.criteria.voucherType) &&
      (!query.voucherNumber || voucher.voucherNumber === query.voucherNumber) &&
      (!query.partyName || voucher.partyName === query.partyName) &&
      (!query.criteria.query || matchesSearch(voucher, query.criteria.query))
    );
    const ordered = [...filtered].sort((left, right) =>
      compareVouchers(left, right, query.criteria.sortBy, query.criteria.sortDirection)
    );
    const totalItems = ordered.length;
    const start = (query.criteria.page - 1) * query.criteria.pageSize;
    return {
      companyId: query.companyId,
      items: ordered.slice(start, start + query.criteria.pageSize).map(toPublicRecord),
      pagination: {
        page: query.criteria.page,
        pageSize: query.criteria.pageSize,
        totalItems,
        totalPages: Math.max(1, Math.ceil(totalItems / query.criteria.pageSize)),
      },
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

function matchesSearch(voucher: VoucherDetails, query: string): boolean {
  const needle = query.toLocaleLowerCase('en-US');
  return [
    voucher.voucherNumber,
    voucher.referenceNumber,
    voucher.partyName,
    voucher.voucherType,
  ].some((value) => value?.toLocaleLowerCase('en-US').includes(needle));
}

function compareVouchers(
  left: VoucherDetails,
  right: VoucherDetails,
  field: 'date' | 'voucherNumber' | 'amount',
  direction: 'asc' | 'desc',
): number {
  const leftValue = field === 'date'
    ? left.date
    : field === 'voucherNumber'
      ? left.voucherNumber ?? ''
      : left.amount?.amount ?? '';
  const rightValue = field === 'date'
    ? right.date
    : field === 'voucherNumber'
      ? right.voucherNumber ?? ''
      : right.amount?.amount ?? '';
  const compared = field === 'amount'
    ? Number(leftValue) - Number(rightValue)
    : leftValue.localeCompare(rightValue, 'en-US');
  const stable = compared || left.voucherId.localeCompare(right.voucherId, 'en-US');
  return direction === 'desc' ? -stable : stable;
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
