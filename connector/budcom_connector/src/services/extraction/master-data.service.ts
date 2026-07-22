import type { ConnectorConfig } from '../../config/defaults.js';
import type { Logger } from '../../infrastructure/logging/logger.js';
import { AppError, ErrorCodes } from '../../infrastructure/errors/app-error.js';
import type { ServiceStatus } from '../../core/types.js';
import type { ErpReadPort } from '../../erp/ports/erp-read-port.js';
import type {
  ExtractorDiagnostics,
  ExtractionResult,
  NormalizedCompanyInfo,
  NormalizedCostCategory,
  NormalizedCostCentre,
  NormalizedGodown,
  NormalizedGstRegistration,
  NormalizedLedger,
  NormalizedLedgerGroup,
  NormalizedStockCategory,
  NormalizedStockGroup,
  NormalizedStockItem,
  NormalizedUnit,
  NormalizedVoucherType,
  PaginatedEnvelope,
  PaginationParams,
} from '../../extraction/core/types.js';
import { MasterDataEntityType } from '../../extraction/core/types.js';
import { paginateItems } from '../../extraction/core/pagination.js';
import { deriveUnitsFromStockItems } from '../../extraction/units/units-derivation.js';
import { assessDerivedUnits } from '../../tally/contracts/response-contract.js';
import type { CompanyResolver } from './company-resolver.js';

export interface MasterDataService {
  start(): Promise<void>;
  stop(): Promise<void>;
  isRunning(): boolean;
  getStatus(): ServiceStatus;
  getCompanyInfo(companyId: string): Promise<NormalizedCompanyInfo>;
  getLedgerGroups(
    companyId: string,
    pagination: PaginationParams,
  ): Promise<PaginatedEnvelope<NormalizedLedgerGroup>>;
  getLedgers(
    companyId: string,
    pagination: PaginationParams,
  ): Promise<PaginatedEnvelope<NormalizedLedger>>;
  getStockGroups(
    companyId: string,
    pagination: PaginationParams,
  ): Promise<PaginatedEnvelope<NormalizedStockGroup>>;
  getStockCategories(
    companyId: string,
    pagination: PaginationParams,
  ): Promise<PaginatedEnvelope<NormalizedStockCategory>>;
  getStockItems(
    companyId: string,
    pagination: PaginationParams,
  ): Promise<PaginatedEnvelope<NormalizedStockItem>>;
  getUnits(
    companyId: string,
    pagination: PaginationParams,
  ): Promise<PaginatedEnvelope<NormalizedUnit>>;
  getGodowns(
    companyId: string,
    pagination: PaginationParams,
  ): Promise<PaginatedEnvelope<NormalizedGodown>>;
  getCostCategories(
    companyId: string,
    pagination: PaginationParams,
  ): Promise<PaginatedEnvelope<NormalizedCostCategory>>;
  getCostCentres(
    companyId: string,
    pagination: PaginationParams,
  ): Promise<PaginatedEnvelope<NormalizedCostCentre>>;
  getVoucherTypes(
    companyId: string,
    pagination: PaginationParams,
  ): Promise<PaginatedEnvelope<NormalizedVoucherType>>;
  getGstRegistrations(
    companyId: string,
    pagination: PaginationParams,
  ): Promise<PaginatedEnvelope<NormalizedGstRegistration>>;
  getExtractorDiagnostics(entityType?: MasterDataEntityType): ExtractorDiagnostics[];
}

export class MasterDataServiceImpl implements MasterDataService {
  private running = false;
  private stockItemsCache = new Map<string, ExtractionResult<NormalizedStockItem>>();
  private unitsDiagnostics = {
    totalExtractions: 0,
    failedExtractions: 0,
    lastExtractedAt: undefined as string | undefined,
    lastDurationMs: undefined as number | undefined,
    lastItemCount: undefined as number | undefined,
    lastErrorAt: undefined as string | undefined,
    lastErrorMessage: undefined as string | undefined,
    source: 'derived-from-stock-items' as const,
  };

  constructor(
    private readonly config: ConnectorConfig,
    private readonly readPort: ErpReadPort,
    private readonly companyResolver: CompanyResolver,
    private readonly logger: Logger,
  ) {}

  async start(): Promise<void> {
    this.running = true;
    this.logger.info('Master data extraction service started');
  }

  async stop(): Promise<void> {
    this.running = false;
    this.stockItemsCache.clear();
    this.logger.info('Master data extraction service stopped');
  }

  isRunning(): boolean {
    return this.running;
  }

  getStatus(): ServiceStatus {
    return {
      name: 'MasterDataExtraction',
      running: this.running,
      ready: this.running && this.readPort.isReady(),
      message: this.running ? 'Extraction ready' : 'Stopped',
    };
  }

  async getCompanyInfo(companyId: string): Promise<NormalizedCompanyInfo> {
    this.assertRunning();
    const companyName = await this.companyResolver.resolveName(companyId);

    try {
      const info = await this.readPort.getCompanyInfo(companyId, companyName);
      if (info) return info;
    } catch (error) {
      this.logger.warn('Company object export failed; falling back to discovery metadata', {
        companyId,
        error: error instanceof Error ? error.message : String(error),
      });
    }

    return {
      id: companyId,
      name: companyName,
      baseCurrency: 'INR',
    };
  }

  async getLedgerGroups(companyId: string, pagination: PaginationParams) {
    return this.extractPaginated(companyId, pagination, (name) =>
      this.readPort.readLedgerGroups(name),
    );
  }

  async getLedgers(companyId: string, pagination: PaginationParams) {
    return this.extractPaginated(companyId, pagination, (name) => this.readPort.readLedgers(name));
  }

  async getStockGroups(companyId: string, pagination: PaginationParams) {
    return this.extractPaginated(companyId, pagination, (name) =>
      this.readPort.readStockGroups(name),
    );
  }

  async getStockCategories(companyId: string, pagination: PaginationParams) {
    return this.extractPaginated(companyId, pagination, (name) =>
      this.readPort.readStockCategories(name),
    );
  }

  async getStockItems(companyId: string, pagination: PaginationParams) {
    this.assertRunning();
    const companyName = await this.companyResolver.resolveName(companyId);
    const result = await this.loadStockItems(companyId, companyName);
    const paged = paginateItems([...result.items], pagination);
    return {
      items: paged.items,
      pagination: paged.pagination,
      schemaVersion: this.config.schemaVersion,
      dataFreshnessAt: new Date().toISOString(),
    };
  }

  async getUnits(companyId: string, pagination: PaginationParams) {
    this.assertRunning();
    const started = Date.now();
    this.unitsDiagnostics.totalExtractions += 1;

    try {
      const companyName = await this.companyResolver.resolveName(companyId);
      const stockItems = await this.loadStockItems(companyId, companyName);
      const units = deriveUnitsFromStockItems(stockItems.items);
      const dataQuality = assessDerivedUnits(units.length, stockItems.items.length);
      const paged = paginateItems(units, pagination);

      this.unitsDiagnostics = {
        ...this.unitsDiagnostics,
        lastExtractedAt: new Date().toISOString(),
        lastDurationMs: Date.now() - started,
        lastItemCount: units.length,
        lastErrorAt: undefined,
        lastErrorMessage: undefined,
      };

      this.logger.info('Units derived from stock items', {
        companyId,
        unitCount: units.length,
        stockItemCount: stockItems.items.length,
        durationMs: Date.now() - started,
      });

      return {
        items: paged.items,
        pagination: paged.pagination,
        schemaVersion: this.config.schemaVersion,
        dataFreshnessAt: new Date().toISOString(),
        dataQuality,
      };
    } catch (error) {
      this.unitsDiagnostics.failedExtractions += 1;
      this.unitsDiagnostics.lastErrorAt = new Date().toISOString();
      this.unitsDiagnostics.lastErrorMessage =
        error instanceof Error ? error.message : String(error);
      throw error;
    }
  }

  async getGodowns(companyId: string, pagination: PaginationParams) {
    return this.extractPaginated(companyId, pagination, (name) => this.readPort.readGodowns(name));
  }

  async getCostCategories(companyId: string, pagination: PaginationParams) {
    return this.extractPaginated(companyId, pagination, (name) =>
      this.readPort.readCostCategories(name),
    );
  }

  async getCostCentres(companyId: string, pagination: PaginationParams) {
    return this.extractPaginated(companyId, pagination, (name) =>
      this.readPort.readCostCentres(name),
    );
  }

  async getVoucherTypes(companyId: string, pagination: PaginationParams) {
    return this.extractPaginated(companyId, pagination, (name) =>
      this.readPort.readVoucherTypes(name),
    );
  }

  async getGstRegistrations(companyId: string, pagination: PaginationParams) {
    return this.extractPaginated(companyId, pagination, (name) =>
      this.readPort.readGstRegistrations(name),
    );
  }

  getExtractorDiagnostics(entityType?: MasterDataEntityType): ExtractorDiagnostics[] {
    if (!this.running) return [];
    const all = [
      ...this.readPort.getReadDiagnostics(entityType),
      {
        entityType: MasterDataEntityType.Unit,
        totalExtractions: this.unitsDiagnostics.totalExtractions,
        failedExtractions: this.unitsDiagnostics.failedExtractions,
        lastExtractedAt: this.unitsDiagnostics.lastExtractedAt,
        lastDurationMs: this.unitsDiagnostics.lastDurationMs,
        lastItemCount: this.unitsDiagnostics.lastItemCount,
        lastErrorAt: this.unitsDiagnostics.lastErrorAt,
        lastErrorMessage: this.unitsDiagnostics.lastErrorMessage,
      },
    ];
    return entityType ? all.filter((d) => d.entityType === entityType) : all;
  }

  private async extractPaginated<T>(
    companyId: string,
    pagination: PaginationParams,
    extract: (companyName: string) => Promise<{ items: readonly T[] }>,
  ): Promise<PaginatedEnvelope<T>> {
    this.assertRunning();
    const companyName = await this.companyResolver.resolveName(companyId);
    const result = await extract(companyName);
    const paged = paginateItems([...result.items], pagination);
    return {
      items: paged.items,
      pagination: paged.pagination,
      schemaVersion: this.config.schemaVersion,
      dataFreshnessAt: new Date().toISOString(),
    };
  }

  private assertRunning(): void {
    if (!this.running) {
      throw new AppError(
        ErrorCodes.SERVICE_UNAVAILABLE,
        'Master data extraction service is not running',
        503,
      );
    }
  }

  private async loadStockItems(
    companyId: string,
    companyName: string,
  ): Promise<ExtractionResult<NormalizedStockItem>> {
    const cached = this.stockItemsCache.get(companyId);
    if (cached) return cached;
    const result = await this.readPort.readStockItems(companyName);
    this.stockItemsCache.set(companyId, result);
    return result;
  }
}
