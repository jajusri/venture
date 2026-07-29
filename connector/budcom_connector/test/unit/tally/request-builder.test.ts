import { describe, expect, it } from 'vitest';

import {
  TallyXmlRequestBuilder,
  type EmbeddedTdlCollectionRequestSpec,
} from '../../../src/tally/xml/request-builder.js';

describe('TallyXmlRequestBuilder', () => {
  const builder = new TallyXmlRequestBuilder();

  it('builds connectivity check request', () => {
    const xml = builder.buildConnectivityCheck();
    expect(xml).toContain('<TALLYREQUEST>Export</TALLYREQUEST>');
    expect(xml).toContain('<ID>License Info</ID>');
  });

  it('builds company list request', () => {
    const xml = builder.buildCompanyListRequest();
    expect(xml).toContain('<ID>List of Companies</ID>');
    expect(xml).toContain('<SVEXPORTFORMAT>$$SysName:XML</SVEXPORTFORMAT>');
  });

  it('escapes XML special characters', () => {
    const xml = builder.build({
      tallyRequest: 'Export',
      type: 'Data',
      id: 'Test & Co',
      description: 'A <B> "C"',
    });
    expect(xml).toContain('<ID>Test &amp; Co</ID>');
    expect(xml).toContain('<DESC>A &lt;B&gt; &quot;C&quot;</DESC>');
  });

  it('defines a request-local collection with an application-defined name and object type', () => {
    const xml = builder.buildEmbeddedCollection({
      collection: {
        name: 'Example Collection',
        objectType: 'Ledger',
      },
    });

    expect(xml).toContain('<TALLYREQUEST>Export</TALLYREQUEST>');
    expect(xml).toContain('<TYPE>Collection</TYPE>');
    expect(xml).toContain('<ID>Example Collection</ID>');
    expect(xml).toContain(
      '<COLLECTION NAME="Example Collection" ISMODIFY="No">',
    );
    expect(xml).toContain('<TYPE>Ledger</TYPE>');
    expect(xml).not.toContain('<NATIVEMETHOD>');
  });

  it('emits explicit native fetch methods in order', () => {
    const xml = builder.buildEmbeddedCollection({
      collection: {
        name: 'Rich Ledgers',
        objectType: 'Ledger',
        fetch: ['Name', 'Parent', 'ClosingBalance'],
      },
    });

    expect(xml).toContain('<NATIVEMETHOD>Name</NATIVEMETHOD>');
    expect(xml).toContain('<NATIVEMETHOD>Parent</NATIVEMETHOD>');
    expect(xml).toContain('<NATIVEMETHOD>ClosingBalance</NATIVEMETHOD>');
    expect(xml.indexOf('Name</NATIVEMETHOD>')).toBeLessThan(
      xml.indexOf('Parent</NATIVEMETHOD>'),
    );
  });

  it('nests optional static variables and emits optional TDL attributes', () => {
    const xml = builder.buildEmbeddedCollection({
      collection: {
        name: 'Company Ledgers',
        objectType: 'Ledger',
        attributes: {
          ISFIXED: 'No',
          ISINITIALIZE: 'Yes',
        },
      },
      staticVariables: {
        SVEXPORTFORMAT: '$$SysName:XML',
        SVCURRENTCOMPANY: 'A & B',
      },
    });

    expect(xml).toContain(
      '<COLLECTION NAME="Company Ledgers" ISMODIFY="No" ISFIXED="No" ISINITIALIZE="Yes">',
    );
    expect(xml).toContain(
      '<STATICVARIABLES>\n        <SVEXPORTFORMAT>$$SysName:XML</SVEXPORTFORMAT>',
    );
    expect(xml).toContain('<SVCURRENTCOMPANY>A &amp; B</SVCURRENTCOMPANY>');
    expect(xml.indexOf('<STATICVARIABLES>')).toBeGreaterThan(xml.indexOf('<DESC>'));
    expect(xml.indexOf('</STATICVARIABLES>')).toBeLessThan(xml.indexOf('<TDL>'));
  });

  it('escapes collection definition content and attribute values', () => {
    const xml = builder.buildEmbeddedCollection({
      collection: {
        name: 'A & B',
        objectType: 'Ledger <Custom>',
        fetch: ['Name & Alias'],
        attributes: { ISOPTION: '"No"' },
      },
    });

    expect(xml).toContain('<ID>A &amp; B</ID>');
    expect(xml).toContain('NAME="A &amp; B"');
    expect(xml).toContain('ISOPTION="&quot;No&quot;"');
    expect(xml).toContain('<TYPE>Ledger &lt;Custom&gt;</TYPE>');
    expect(xml).toContain('<NATIVEMETHOD>Name &amp; Alias</NATIVEMETHOD>');
  });

  it.each([
    {
      label: 'missing collection name',
      spec: { collection: { name: '', objectType: 'Ledger' } },
      error: 'Collection name must be a non-empty string.',
    },
    {
      label: 'missing object type',
      spec: { collection: { name: 'Example', objectType: ' ' } },
      error: 'Collection object type must be a non-empty string.',
    },
    {
      label: 'surrounding whitespace',
      spec: { collection: { name: ' Example', objectType: 'Ledger' } },
      error: 'Collection name must not have surrounding whitespace.',
    },
    {
      label: 'blank fetch method',
      spec: { collection: { name: 'Example', objectType: 'Ledger', fetch: [''] } },
      error: 'Fetch method must be a non-empty string.',
    },
    {
      label: 'duplicate fetch method',
      spec: { collection: { name: 'Example', objectType: 'Ledger', fetch: ['Name', 'name'] } },
      error: 'Duplicate fetch method: name',
    },
    {
      label: 'invalid static-variable XML name',
      spec: {
        collection: { name: 'Example', objectType: 'Ledger' },
        staticVariables: { 'NOT VALID': 'value' },
      },
      error: 'Invalid static variable name: NOT VALID',
    },
    {
      label: 'reserved collection attribute',
      spec: {
        collection: {
          name: 'Example',
          objectType: 'Ledger',
          attributes: { ISMODIFY: 'Yes' },
        },
      },
      error: 'Collection attribute ISMODIFY is managed by the request builder.',
    },
    {
      label: 'invalid collection attribute XML name',
      spec: {
        collection: {
          name: 'Example',
          objectType: 'Ledger',
          attributes: { 'BAD ATTRIBUTE': 'No' },
        },
      },
      error: 'Invalid collection attribute name: BAD ATTRIBUTE',
    },
  ])('rejects malformed embedded collection input: $label', ({ spec, error }) => {
    expect(() =>
      builder.buildEmbeddedCollection(spec as EmbeddedTdlCollectionRequestSpec),
    ).toThrow(error);
  });

  it('preserves the existing collectionModifyFetch request shape', () => {
    const xml = builder.build({
      tallyRequest: 'Export',
      type: 'Collection',
      id: 'List of Ledgers',
      staticVariables: { SVEXPORTFORMAT: '$$SysName:XML' },
      collectionModifyFetch: ['NAME', 'PARENT'],
    });

    expect(xml).toContain('<COLLECTION NAME="List of Ledgers" ISMODIFY="Yes">');
    expect(xml).toContain('<ADD>Fetch : NAME, PARENT</ADD>');
    expect(xml).not.toContain('<NATIVEMETHOD>');
  });
});
