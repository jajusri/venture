export interface TallyXmlRequestSpec {
  readonly tallyRequest: string;
  readonly type: string;
  readonly id: string;
  readonly description?: string;
  readonly staticVariables?: Readonly<Record<string, string>>;
  readonly version?: string;
  /** When set, modifies the default Tally collection export to FETCH additional fields. */
  readonly collectionModifyFetch?: readonly string[];
}

export interface EmbeddedTdlCollectionDefinition {
  readonly name: string;
  readonly objectType?: string;
  readonly sourceCollection?: string;
  readonly walk?: string;
  readonly fetch?: readonly string[];
  readonly compute?: Readonly<Record<string, string>>;
  readonly attributes?: Readonly<Record<string, string>>;
  readonly filters?: readonly string[];
}

export interface EmbeddedTdlCollectionRequestSpec {
  readonly collection: EmbeddedTdlCollectionDefinition;
  readonly supportingCollections?: readonly EmbeddedTdlCollectionDefinition[];
  readonly staticVariables?: Readonly<Record<string, string>>;
  readonly version?: string;
  readonly systemFormulae?: Readonly<Record<string, string>>;
}

export class TallyXmlRequestBuilder {
  build(spec: TallyXmlRequestSpec): string {
    const version = spec.version ?? '1';
    const staticVariables = spec.staticVariables ?? {};
    const staticXml = Object.entries(staticVariables)
      .map(([key, value]) => `      <${key}>${escapeXml(value)}</${key}>`)
      .join('\n');

    const body =
      spec.description || Object.keys(staticVariables).length > 0 || spec.collectionModifyFetch?.length
        ? spec.collectionModifyFetch?.length
          ? `<BODY>
    <DESC>
    ${
      Object.keys(staticVariables).length > 0
        ? `<STATICVARIABLES>
${staticXml}
    </STATICVARIABLES>`
        : ''
    }
      <TDL>
        <TDLMESSAGE>
          <COLLECTION NAME="${escapeXml(spec.id)}" ISMODIFY="Yes">
            <ADD>Fetch : ${escapeXml(spec.collectionModifyFetch.join(', '))}</ADD>
          </COLLECTION>
        </TDLMESSAGE>
      </TDL>
    </DESC>
  </BODY>`
          : `<BODY>
    ${spec.description ? `<DESC>${escapeXml(spec.description)}</DESC>` : ''}
    ${
      Object.keys(staticVariables).length > 0
        ? `<STATICVARIABLES>
${staticXml}
    </STATICVARIABLES>`
        : ''
    }
  </BODY>`
        : '';

    return `<ENVELOPE>
  <HEADER>
    <VERSION>${escapeXml(version)}</VERSION>
    <TALLYREQUEST>${escapeXml(spec.tallyRequest)}</TALLYREQUEST>
    <TYPE>${escapeXml(spec.type)}</TYPE>
    <ID>${escapeXml(spec.id)}</ID>
  </HEADER>
  ${body}
</ENVELOPE>`;
  }

  /**
   * Defines and exports a request-local TDL collection. This path is deliberately
   * separate from collectionModifyFetch, which alters an existing Tally collection.
   */
  buildEmbeddedCollection(spec: EmbeddedTdlCollectionRequestSpec): string {
    validateEmbeddedCollectionSpec(spec);

    const version = spec.version ?? '1';
    const { collection } = spec;
    const staticVariables = Object.entries(spec.staticVariables ?? {})
      .map(([name, value]) => `        <${name}>${escapeXml(value)}</${name}>`)
      .join('\n');
    const collections = [...(spec.supportingCollections ?? []), collection]
      .map(renderEmbeddedCollectionDefinition)
      .join('\n');
    const formulae = Object.entries(spec.systemFormulae ?? {})
      .map(([name, formula]) =>
        `          <SYSTEM TYPE="Formulae" NAME="${escapeXml(name)}">${escapeXml(formula)}</SYSTEM>`)
      .join('\n');

    return `<ENVELOPE>
  <HEADER>
    <VERSION>${escapeXml(version)}</VERSION>
    <TALLYREQUEST>Export</TALLYREQUEST>
    <TYPE>Collection</TYPE>
    <ID>${escapeXml(collection.name)}</ID>
  </HEADER>
  <BODY>
    <DESC>
${staticVariables ? `      <STATICVARIABLES>\n${staticVariables}\n      </STATICVARIABLES>\n` : ''}      <TDL>
        <TDLMESSAGE>
${collections}
${formulae ? `${formulae}\n` : ''}
        </TDLMESSAGE>
      </TDL>
    </DESC>
  </BODY>
</ENVELOPE>`;
  }

  /** Lightweight connectivity probe — does not export business data. */
  buildConnectivityCheck(): string {
    return this.build({
      tallyRequest: 'Export',
      type: 'Data',
      id: 'License Info',
    });
  }

  /** Company discovery request — lists available companies only. */
  buildCompanyListRequest(): string {
    return this.build({
      tallyRequest: 'Export',
      type: 'Collection',
      id: 'List of Companies',
      description: 'List of Companies',
      staticVariables: {
        SVEXPORTFORMAT: '$$SysName:XML',
      },
    });
  }
}

function escapeXml(value: string): string {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&apos;');
}

const XML_NAME = /^[A-Za-z_][A-Za-z0-9_.-]*$/;
const RESERVED_COLLECTION_ATTRIBUTES = new Set(['NAME', 'ISMODIFY']);

function validateEmbeddedCollectionSpec(spec: EmbeddedTdlCollectionRequestSpec): void {
  if (!spec || typeof spec !== 'object') {
    throw new Error('Embedded collection request specification is required.');
  }
  if (!spec.collection || typeof spec.collection !== 'object') {
    throw new Error('Embedded collection definition is required.');
  }

  requireNonBlank(spec.collection.name, 'Collection name');
  validateCollectionDefinition(spec.collection);
  for (const collection of spec.supportingCollections ?? []) {
    validateCollectionDefinition(collection);
  }
  if (spec.version !== undefined) requireNonBlank(spec.version, 'Request version');

  validateStringRecord(spec.staticVariables, 'Static variable');
  validateStringRecord(spec.systemFormulae, 'System formula');
}

function renderEmbeddedCollectionDefinition(
  collection: EmbeddedTdlCollectionDefinition,
): string {
  const attributes = Object.entries(collection.attributes ?? {})
    .map(([name, value]) => ` ${name}="${escapeXml(value)}"`)
    .join('');
  const fields = [
    collection.objectType ? `            <TYPE>${escapeXml(collection.objectType)}</TYPE>` : '',
    collection.sourceCollection
      ? `            <SOURCECOLLECTION>${escapeXml(collection.sourceCollection)}</SOURCECOLLECTION>`
      : '',
    collection.walk ? `            <WALK>${escapeXml(collection.walk)}</WALK>` : '',
    ...(collection.filters ?? [])
      .map((filter) => `            <FILTERS>${escapeXml(filter)}</FILTERS>`),
    ...(collection.fetch ?? [])
      .map((method) => `            <NATIVEMETHOD>${escapeXml(method)}</NATIVEMETHOD>`),
    ...Object.entries(collection.compute ?? {})
      .map(([name, formula]) =>
        `            <COMPUTE>${escapeXml(`${name} : ${formula}`)}</COMPUTE>`),
  ].filter(Boolean).join('\n');
  return `          <COLLECTION NAME="${escapeXml(collection.name)}" ISMODIFY="No"${attributes}>
${fields}
          </COLLECTION>`;
}

function validateCollectionDefinition(
  collection: EmbeddedTdlCollectionDefinition,
): void {
  requireNonBlank(collection.name, 'Collection name');
  const hasObjectType = collection.objectType !== undefined;
  const hasSource = collection.sourceCollection !== undefined;
  if (hasObjectType === hasSource) {
    throw new Error('Collection must define exactly one of objectType or sourceCollection.');
  }
  if (collection.objectType !== undefined) {
    requireNonBlank(collection.objectType, 'Collection object type');
  }
  if (collection.sourceCollection !== undefined) {
    requireNonBlank(collection.sourceCollection, 'Collection source');
  }
  if (collection.walk !== undefined) requireNonBlank(collection.walk, 'Collection walk');
  validateStringRecord(collection.attributes, 'Collection attribute', {
    reservedNames: RESERVED_COLLECTION_ATTRIBUTES,
  });
  validateStringRecord(collection.compute, 'Collection compute');

  if (collection.fetch !== undefined && !Array.isArray(collection.fetch)) {
    throw new Error('Collection fetch list must be an array.');
  }
  const seenFetches = new Set<string>();
  for (const method of collection.fetch ?? []) {
    requireNonBlank(method, 'Fetch method');
    const normalized = method.toUpperCase();
    if (seenFetches.has(normalized)) throw new Error(`Duplicate fetch method: ${method}`);
    seenFetches.add(normalized);
  }
  if (collection.filters !== undefined && !Array.isArray(collection.filters)) {
    throw new Error('Collection filter list must be an array.');
  }
  for (const filter of collection.filters ?? []) {
    requireNonBlank(filter, 'Collection filter');
    if (!XML_NAME.test(filter)) throw new Error(`Invalid collection filter name: ${filter}`);
  }
}

function validateStringRecord(
  record: Readonly<Record<string, string>> | undefined,
  label: string,
  options: { readonly reservedNames?: ReadonlySet<string> } = {},
): void {
  if (record === undefined) return;
  if (!record || typeof record !== 'object' || Array.isArray(record)) {
    throw new Error(`${label} values must be an object.`);
  }
  for (const [name, value] of Object.entries(record)) {
    if (!XML_NAME.test(name)) throw new Error(`Invalid ${label.toLowerCase()} name: ${name}`);
    if (options.reservedNames?.has(name.toUpperCase())) {
      throw new Error(`${label} ${name} is managed by the request builder.`);
    }
    if (typeof value !== 'string') throw new Error(`${label} ${name} must be a string.`);
  }
}

function requireNonBlank(value: unknown, label: string): asserts value is string {
  if (typeof value !== 'string' || value.trim().length === 0) {
    throw new Error(`${label} must be a non-empty string.`);
  }
  if (value !== value.trim()) throw new Error(`${label} must not have surrounding whitespace.`);
}
