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
