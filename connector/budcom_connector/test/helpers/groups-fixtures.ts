export const GROUP_ONE_ROOT = `<ENVELOPE>
  <BODY>
    <DATA>
      <COLLECTION>
        <GROUP NAME="Capital Account">
          <NAME>Capital Account</NAME>
          <PARENT>Primary</PARENT>
        </GROUP>
      </COLLECTION>
    </DATA>
  </BODY>
</ENVELOPE>`;

export const GROUP_MULTIPLE_HIERARCHY = `<ENVELOPE>
  <BODY>
    <DATA>
      <COLLECTION>
        <GROUP NAME="Primary">
          <NAME>Primary</NAME>
          <ISBUILTIN>Yes</ISBUILTIN>
        </GROUP>
        <GROUP NAME="Current Assets">
          <NAME>Current Assets</NAME>
          <PARENT>Primary</PARENT>
        </GROUP>
        <GROUP NAME="Sundry Debtors">
          <NAME>Sundry Debtors</NAME>
          <PARENT>Current Assets</PARENT>
          <ISREVENUE>No</ISREVENUE>
        </GROUP>
        <GROUP NAME="Sales Accounts">
          <NAME>Sales Accounts</NAME>
          <PARENT>Primary</PARENT>
          <ISREVENUE>Yes</ISREVENUE>
        </GROUP>
      </COLLECTION>
    </DATA>
  </BODY>
</ENVELOPE>`;

export const GROUP_DEEP_HIERARCHY = `<ENVELOPE>
  <BODY>
    <DATA>
      <COLLECTION>
        <GROUP><NAME>Level 1</NAME><PARENT>Primary</PARENT></GROUP>
        <GROUP><NAME>Level 2</NAME><PARENT>Level 1</PARENT></GROUP>
        <GROUP><NAME>Level 3</NAME><PARENT>Level 2</PARENT></GROUP>
        <GROUP><NAME>Level 4</NAME><PARENT>Level 3</PARENT></GROUP>
      </COLLECTION>
    </DATA>
  </BODY>
</ENVELOPE>`;

export const GROUP_DUPLICATE_IDENTICAL = `<ENVELOPE>
  <BODY>
    <DATA>
      <COLLECTION>
        <GROUP><NAME>Cash-in-Hand</NAME><PARENT>Current Assets</PARENT></GROUP>
        <GROUP><NAME>Cash-in-Hand</NAME><PARENT>Current Assets</PARENT></GROUP>
      </COLLECTION>
    </DATA>
  </BODY>
</ENVELOPE>`;

export const GROUP_DUPLICATE_NAME_DIFFERENT_PARENT = `<ENVELOPE>
  <BODY>
    <DATA>
      <COLLECTION>
        <GROUP NAME="Branch A"><NAME>Branch A</NAME><PARENT>Primary</PARENT></GROUP>
        <GROUP NAME="Branch A "><NAME>Branch A </NAME><PARENT>Sales Accounts</PARENT></GROUP>
      </COLLECTION>
    </DATA>
  </BODY>
</ENVELOPE>`;

export const GROUP_MISSING_PARENT = `<ENVELOPE>
  <BODY>
    <DATA>
      <COLLECTION>
        <GROUP><NAME>Orphan Group</NAME><PARENT>Nonexistent Parent</PARENT></GROUP>
      </COLLECTION>
    </DATA>
  </BODY>
</ENVELOPE>`;

export const GROUP_SELF_PARENT = `<ENVELOPE>
  <BODY>
    <DATA>
      <COLLECTION>
        <GROUP><NAME>Loop Group</NAME><PARENT>Loop Group</PARENT></GROUP>
      </COLLECTION>
    </DATA>
  </BODY>
</ENVELOPE>`;

export const GROUP_CYCLE = `<ENVELOPE>
  <BODY>
    <DATA>
      <COLLECTION>
        <GROUP><NAME>Group A</NAME><PARENT>Group B</PARENT></GROUP>
        <GROUP><NAME>Group B</NAME><PARENT>Group A</PARENT></GROUP>
      </COLLECTION>
    </DATA>
  </BODY>
</ENVELOPE>`;

export const GROUP_EMPTY_LIST = `<ENVELOPE>
  <BODY>
    <DESC><CMPINFO><GROUP>0</GROUP></CMPINFO></DESC>
    <DATA><COLLECTION></COLLECTION></DATA>
  </BODY>
</ENVELOPE>`;

export const GROUP_MISSING_NAME = `<ENVELOPE>
  <BODY>
    <DATA>
      <COLLECTION>
        <GROUP></GROUP>
        <GROUP><NAME></NAME></GROUP>
        <GROUP><PARENT>Primary</PARENT></GROUP>
      </COLLECTION>
    </DATA>
  </BODY>
</ENVELOPE>`;

export const GROUP_WHITESPACE = `<ENVELOPE>
  <BODY>
    <DATA>
      <COLLECTION>
        <GROUP><NAME>  Sundry Debtors  </NAME><PARENT>  Current Assets  </PARENT></GROUP>
      </COLLECTION>
    </DATA>
  </BODY>
</ENVELOPE>`;

export const GROUP_UNICODE = `<ENVELOPE>
  <BODY>
    <DATA>
      <COLLECTION>
        <GROUP><NAME>Müller &amp; Söhne Gruppe</NAME><PARENT>Primary</PARENT></GROUP>
      </COLLECTION>
    </DATA>
  </BODY>
</ENVELOPE>`;

export const GROUP_UNEXPECTED_ENVELOPE = `<RESPONSE><DATA/></RESPONSE>`;

export const GROUP_MALFORMED_XML = `<ENVELOPE><BODY><DATA><COLLECTION>`;

export const GROUP_PARTIAL_TRUNCATED = `<ENVELOPE><BODY><DATA><COLLECTION><GROUP><NAME>Valid</NAME></GROUP>`;

export function buildLargeHierarchyXml(depth: number): string {
  const groups: string[] = [];
  groups.push('<GROUP><NAME>Root</NAME><PARENT>Primary</PARENT></GROUP>');
  for (let i = 1; i < depth; i += 1) {
    const parent = i === 1 ? 'Root' : `Node-${i - 1}`;
    groups.push(`<GROUP><NAME>Node-${i}</NAME><PARENT>${parent}</PARENT></GROUP>`);
  }
  return `<ENVELOPE><BODY><DATA><COLLECTION>${groups.join('')}</COLLECTION></DATA></BODY></ENVELOPE>`;
}

export function buildWideHierarchyXml(count: number): string {
  const groups: string[] = [
    '<GROUP><NAME>Primary</NAME><PARENT>Primary</PARENT></GROUP>',
  ];
  for (let i = 0; i < count; i += 1) {
    groups.push(`<GROUP><NAME>Leaf-${i}</NAME><PARENT>Primary</PARENT></GROUP>`);
  }
  return `<ENVELOPE><BODY><DATA><COLLECTION>${groups.join('')}</COLLECTION></DATA></BODY></ENVELOPE>`;
}
