import fs from 'node:fs';
import path from 'node:path';

import { loadConfig } from '../../config/index.js';
import { createLogger } from '../../infrastructure/logging/logger.js';
import { TallyHttpTransport } from '../../tally/transport/tally-http-transport.js';
import {
  APPROVED_XML_PARSER_MAX_DEPTH,
  APPROVED_XML_PARSER_MAX_NODE_COUNT,
} from '../../tally/xml/response-parser-limits.js';
import type { VoucherDiscoveryManifest } from '../voucher-fixture-manifest.js';
import {
  executeVoucherDiscovery,
  VOUCHER_DISCOVERY_EXPERIMENT_LABELS,
  type VoucherDiscoveryExperimentLabel,
} from '../voucher-discovery-executor.js';

interface CliOptions {
  readonly manifestPath: string;
  readonly company: string;
  readonly tallyHost: string;
  readonly tallyPort: number;
  readonly productionDenylistPath: string;
  readonly approvedOutputRoot: string;
  readonly outputDirectory: string;
  readonly restrictedOutputRoot: string;
  readonly restrictedOutputDirectory: string;
  readonly timeoutMs: number;
  readonly maximumResponseBytes: number;
  readonly maxXmlDepth: number;
  readonly maxXmlNodeCount: number;
  readonly reviewerAcknowledgment: string;
  readonly experimentLabel: VoucherDiscoveryExperimentLabel;
  readonly strictSanitization: boolean;
  readonly acknowledgeFixtureOnly: boolean;
}

function valueOf(args: readonly string[], name: string): string {
  const index = args.indexOf(name);
  const value = index >= 0 ? args[index + 1] : undefined;
  if (!value || value.startsWith('--')) throw new Error(`Required argument missing: ${name}`);
  return value;
}

function positiveInteger(args: readonly string[], name: string): number {
  const value = Number(valueOf(args, name));
  if (!Number.isSafeInteger(value) || value <= 0) throw new Error(`${name} must be a positive integer.`);
  return value;
}

function parseCliOptions(args: readonly string[]): CliOptions {
  const experiment = valueOf(args, '--experiment');
  if (!VOUCHER_DISCOVERY_EXPERIMENT_LABELS.includes(experiment as VoucherDiscoveryExperimentLabel)) {
    throw new Error('Unsupported discovery experiment label.');
  }
  return {
    manifestPath: path.resolve(valueOf(args, '--manifest')),
    company: valueOf(args, '--company'),
    tallyHost: valueOf(args, '--tally-host'),
    tallyPort: positiveInteger(args, '--tally-port'),
    productionDenylistPath: path.resolve(valueOf(args, '--production-company-denylist')),
    approvedOutputRoot: path.resolve(valueOf(args, '--approved-output-root')),
    outputDirectory: path.resolve(valueOf(args, '--output-directory')),
    restrictedOutputRoot: path.resolve(valueOf(args, '--restricted-output-root')),
    restrictedOutputDirectory: path.resolve(valueOf(args, '--restricted-output-directory')),
    timeoutMs: positiveInteger(args, '--timeout-ms'),
    maximumResponseBytes: positiveInteger(args, '--max-response-bytes'),
    maxXmlDepth: positiveInteger(args, '--max-xml-depth'),
    maxXmlNodeCount: positiveInteger(args, '--max-xml-node-count'),
    reviewerAcknowledgment: valueOf(args, '--reviewer-acknowledgment'),
    experimentLabel: experiment as VoucherDiscoveryExperimentLabel,
    strictSanitization: args.includes('--strict-sanitization'),
    acknowledgeFixtureOnly: args.includes('--acknowledge-fixture-only'),
  };
}

function readJson<T>(filePath: string): T {
  if (!fs.existsSync(filePath) || !fs.lstatSync(filePath).isFile()) {
    throw new Error('Required local input file does not exist.');
  }
  return JSON.parse(fs.readFileSync(filePath, 'utf8')) as T;
}

async function main(): Promise<void> {
  const options = parseCliOptions(process.argv.slice(2));
  const manifest = readJson<VoucherDiscoveryManifest>(options.manifestPath);
  const productionCompanyNames = readJson<string[]>(options.productionDenylistPath);
  if (!Array.isArray(productionCompanyNames) || !productionCompanyNames.every((item) => typeof item === 'string')) {
    throw new Error('Production company denylist must be a JSON string array.');
  }
  if (options.maxXmlDepth > APPROVED_XML_PARSER_MAX_DEPTH) {
    throw new Error('Requested XML depth exceeds the approved parser maximum.');
  }
  if (options.maxXmlNodeCount > APPROVED_XML_PARSER_MAX_NODE_COUNT) {
    throw new Error('Requested XML node count exceeds the approved parser maximum.');
  }

  const logger = createLogger({ service: 'voucher-discovery-cli', level: 'info' });
  const config = loadConfig({
    tallyHost: options.tallyHost,
    tallyPort: options.tallyPort,
    tallyTimeoutMs: options.timeoutMs,
    tallyMaxResponseBytes: options.maximumResponseBytes,
  });
  const transport = new TallyHttpTransport({ config, logger });
  try {
    const result = await executeVoucherDiscovery(
      {
        fixtureManifestPath: options.manifestPath,
        manifest,
        operatorCompany: options.company,
        productionCompanyNames,
        sanitizationMode: options.strictSanitization ? 'strict' : 'disabled',
        maximumResponseBytes: options.maximumResponseBytes,
        timeoutMs: options.timeoutMs,
        maxXmlDepth: options.maxXmlDepth,
        maxXmlNodeCount: options.maxXmlNodeCount,
        reviewerAcknowledgment: options.reviewerAcknowledgment,
        acknowledgeFixtureOnly: options.acknowledgeFixtureOnly,
        experimentLabel: options.experimentLabel,
        approvedOutputRoot: options.approvedOutputRoot,
        outputDirectory: options.outputDirectory,
        restrictedOutputRoot: options.restrictedOutputRoot,
        restrictedOutputDirectory: options.restrictedOutputDirectory,
        repositoryRoot: path.resolve(process.cwd(), '..', '..'),
      },
      transport,
    );
    logger.info('Voucher discovery completed', {
      operationId: 'VOUCHER_DAY_BOOK_DISCOVERY_CANDIDATE',
      durationMs: result.durationMs,
      responseBytes: result.responseBytes,
      observedVoucherCount: result.observedVoucherCount,
      resultState: result.resultState,
    });
  } finally {
    await transport.close();
  }
}

main().catch((error: unknown) => {
  const message = error instanceof Error ? error.message : 'Voucher discovery failed.';
  console.error(JSON.stringify({ level: 'error', state: 'failed', message }));
  process.exitCode = 1;
});
