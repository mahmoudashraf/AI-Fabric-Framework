import { createContext, useContext, useId, type ReactNode } from 'react'
import AddOutlined from '@mui/icons-material/AddOutlined'
import DeleteOutline from '@mui/icons-material/DeleteOutline'
import { at, audience, audienceModes, datasetList, datasetPath, knowledgeIndex, numericValue, objectValue, sourceKind, sourceNames, textAt, type DocumentPath, type ExactObject, type SourceKind } from './creatorDocument'
import type { PluginIssue } from './types'

export type EditDocument = (edits: { path: DocumentPath; value: unknown; remove?: boolean }[]) => void
export const NumericDraftContext = createContext<{ drafts: Record<string, string>; setDraft: (path: string, value: string | undefined) => void }>({ drafts: {}, setDraft: () => {} })
type FieldProps = {
  document: ExactObject; path: DocumentPath; label: string; edit: EditDocument; disabled?: boolean
  options?: { value: string; label: string }[]; multiline?: boolean; numeric?: boolean; hint?: string; placeholder?: string; issues?: PluginIssue[]
}
function issuePath(path: DocumentPath) { return '/' + path.map(key => String(key).replace(/~/g, '~0').replace(/\//g, '~1')).join('/') }
export function Field({ document, path, label, edit, disabled, options, multiline, numeric, hint, placeholder, issues }: FieldProps) {
  const id = useId()
  const numbers = useContext(NumericDraftContext)
  const draftKey = issuePath(path)
  const pending = numbers.drafts[draftKey]
  const value = pending ?? textAt(document, path)
  const raw = at(document, path)
  const fieldIssue = issues?.find(issue => issue.path === issuePath(path) || issue.path === path.join('.'))
  const error = pending !== undefined ? 'Incomplete number. Finish this value before saving or checking.' : fieldIssue?.message
  const describedBy = error || hint || raw === null ? `${id}-help` : undefined
  const change = (next: string) => {
    let field: unknown = next
    if (numeric) {
      try { field = numericValue(next) }
      catch { numbers.setDraft(draftKey, next); return }
    }
    edit([{ path, value: field }])
    if (numeric) numbers.setDraft(draftKey, undefined)
  }
  return <label className="pl-field" htmlFor={id}><span id={`${id}-label`}>{label}</span>
    {options ? <select id={id} aria-labelledby={`${id}-label`} value={value} disabled={disabled} onChange={event => change(event.target.value)} aria-invalid={Boolean(error)} aria-describedby={describedBy}>
      {!options.some(option => option.value === value) && <option value={value}>{value || (raw === null ? 'Explicit null' : 'Not set')}</option>}
      {options.map(option => <option key={option.value} value={option.value}>{option.label}</option>)}
    </select> : multiline ? <textarea id={id} aria-labelledby={`${id}-label`} rows={3} value={value} disabled={disabled} placeholder={placeholder} onChange={event => change(event.target.value)} aria-invalid={Boolean(error)} aria-describedby={describedBy} />
      : <input id={id} aria-labelledby={`${id}-label`} value={value} disabled={disabled} placeholder={placeholder} inputMode={numeric ? 'decimal' : undefined} onChange={event => change(event.target.value)} aria-invalid={Boolean(error)} aria-describedby={describedBy} />}
    {(error || hint || raw === null) && <small id={`${id}-help`} className={error ? 'pl-error-text' : undefined}>{error || hint || 'Explicit null'}</small>}
  </label>
}
function CheckField({ document, path, label, edit, disabled, hint }: Omit<FieldProps, 'options'>) {
  const value = at(document, path)
  const id = useId()
  return <div><label className="pl-checkbox"><input type="checkbox" checked={value === true} disabled={disabled} aria-describedby={hint ? `${id}-help` : undefined} onChange={event => edit([{ path, value: event.target.checked }])} />{label}{value !== true && value !== false && value !== undefined && <span className="pl-caption">Custom source value retained</span>}</label>{hint && <small id={`${id}-help`} className="pl-check-hint">{hint}</small>}</div>
}
const options = (values: string[]) => values.map(value => ({ value, label: value }))

function PointerMap({ document, path, label, edit, disabled, hint, placeholder }: Omit<FieldProps, 'issues'>) {
  const value = at(document, path)
  const id = useId()
  if (value !== undefined && !objectValue(value)) return <div className="pl-notice pl-warning">{label} has a custom source value. Edit it in Source.</div>
  const entries = Object.entries(objectValue(value) ? value : {})
  return <section className="pl-mapping-section"><h3>{label}</h3>{hint && <p className="pl-field-help" id={`${id}-help`}>{hint}</p>}<div className="pl-table-scroll"><table className="pl-mapping"><thead><tr><th>JSON path in each record</th><th>Indexed field name</th><th><span className="pl-sr-only">Remove</span></th></tr></thead><tbody>
    {entries.map(([key, pointer]) => <tr key={key}><td><input aria-label={`${label}: ${key} source field`} aria-describedby={hint ? `${id}-help` : undefined} placeholder={placeholder || 'e.g. /description'} value={typeof pointer === 'string' ? pointer : ''} disabled={disabled} onChange={event => edit([{ path: [...path, key], value: event.target.value }])} /></td>
      <td><input aria-label={`${label}: ${key} meaning`} aria-describedby={hint ? `${id}-help` : undefined} placeholder="e.g. description" defaultValue={key} disabled={disabled} onBlur={event => {
        const next = event.target.value
        if (next === key || !next || Object.prototype.hasOwnProperty.call(value, next)) { event.target.value = key; return }
        edit([{ path: [...path, key], value: undefined, remove: true }, { path: [...path, next], value: pointer }])
      }} /></td><td><button type="button" className="pl-icon-button" aria-label={`Remove ${key}`} title={`Remove ${key}`} disabled={disabled} onClick={() => edit([{ path: [...path, key], value: undefined, remove: true }])}><DeleteOutline /></button></td></tr>)}
  </tbody></table></div><button type="button" className="pl-text-button" disabled={disabled} onClick={() => {
    let next = entries.length + 1
    while (Object.prototype.hasOwnProperty.call(value ?? {}, `field${next}`)) next++
    edit([{ path: [...path, `field${next}`], value: '' }])
  }}><AddOutlined />Add field</button></section>
}

type Props = { document: ExactObject; step: number; dataset: number; edit: EditDocument; disabled: boolean; immutableId: boolean; issues: PluginIssue[]; onSourceChange: (kind: SourceKind) => void }
export function CreatorFields({ document, step, dataset, edit, disabled, immutableId, issues, onSourceChange }: Props) {
  const d = datasetPath(dataset), c = [...d, 'syncConnector'], h = [...c, 'httpSource'], p = [...c, 'connectionProfile'], auth = [...p, 'auth']
  const kind = sourceKind(document, dataset), documents = kind === 'S3_COMPATIBLE_OBJECT_STORAGE', sql = kind === 'SQL_QUERY', http = kind === 'HTTP_JSON'
  const sourceIndex = knowledgeIndex(document, dataset)
  const fields = (path: DocumentPath, label: string, props: Partial<FieldProps> = {}) => <Field document={document} path={path} label={label} edit={edit} disabled={disabled} issues={issues} {...props} />
  const check = (path: DocumentPath, label: string, hint: string) => <CheckField document={document} path={path} label={label} hint={hint} edit={edit} disabled={disabled} />
  const key = (suffix: DocumentPath) => [...d, 'documentPolicy', ...suffix]
  let content: ReactNode
  if (step === 0) return <>
    {fields(['displayName'], 'Display name', { placeholder: 'e.g. Customer help articles', hint: 'The name shown in your plugin library and when adding knowledge to a Smart Brain.' })}
    {fields(['description'], 'Purpose', { multiline: true, placeholder: 'e.g. Keep approved help articles up to date so the AI can answer product and delivery questions.', hint: 'Describe the information this source provides and the questions it should help answer.' })}
    {fields(['category'], 'Content category', { placeholder: 'e.g. customer-support', hint: 'An optional catalogue label for organizing the plugin. This is not an access rule or an AI search instruction.' })}
    <details><summary>Technical identity</summary>{fields(['pluginId'], 'Plugin identifier', { disabled: disabled || immutableId, hint: 'Generated for this definition. It stays fixed after the first reusable version.' })}<Field document={document} path={[...d, 'datasetId']} label="Dataset identifier" hint="Groups records from this source. Keep it stable so later syncs update the same dataset." edit={edits => {
      const previous = textAt(document, [...d, 'datasetId'])
      const sources = at(document, ['contributions', 'knowledgeSources'])
      if (Array.isArray(sources)) sources.forEach((source, index) => {
        if (textAt(source, ['datasetRef']) === previous) edits.push({ path: ['contributions', 'knowledgeSources', index, 'datasetRef'], value: edits[0].value })
      })
      edit(edits)
    }} disabled={disabled} />{fields([...d, 'entityType'], 'Record type', { placeholder: 'e.g. record', hint: 'Must match an entity defined in this plugin. The starter includes record for HTTP data and document for files.' })}</details>
  </>
  if (step === 1) {
    content = <><div className="pl-source-choices" role="group" aria-label="Source contract">{(['HTTP_JSON', 'S3_COMPATIBLE_OBJECT_STORAGE'] as SourceKind[]).map(option => <button key={option} type="button" className={kind === option ? 'pl-selected' : ''} aria-pressed={kind === option} disabled={disabled} onClick={() => option !== kind && onSourceChange(option)}><strong>{sourceNames[option]}</strong></button>)}</div>
      <details open={sql || undefined}><summary>Advanced source contracts</summary><button type="button" disabled aria-pressed={sql}><strong>SQL connection</strong><small>Requires a qualified customer adapter</small></button></details>
      {http && <>
        <div className="pl-notice">Setup field names below are reusable placeholders, not connection values. The actual API URL, account and secret references are supplied when connecting this plugin to a Smart Brain.</div>
        {fields([...p, 'managedBaseUrlField'], 'Provider base URL input', { placeholder: 'providerBaseUrl', hint: 'Setup field name, not a URL. Starter: providerBaseUrl. Its installer value could be https://api.example.com.' })}
        {fields([...p, 'managedAllowedHostField'], 'Allowed host input', { placeholder: 'providerHost', hint: 'Setup field name for the approved API hostname, for example api.example.com, without https:// or a path.' })}
        {fields([...auth, 'strategy'], 'Authentication', { options: [{ value: 'API_KEY', label: 'API key reference' }, { value: 'FORM_TOKEN_EXCHANGE', label: 'Form token exchange' }] })}
        {textAt(document, [...auth, 'strategy']) === 'API_KEY' ? <div className="pl-two">{fields([...auth, 'apiKeyHeader'], 'API key header', { placeholder: 'X-API-Key', hint: 'HTTP header expected by the provider. Starter: X-API-Key; use the exact name from its API documentation.' })}{fields([...auth, 'apiKeySecretRefField'], 'Credential reference input', { placeholder: 'providerKey', hint: 'Setup field name for a stored Platform secret reference. Never enter the API key itself here.' })}</div> : <>
          {fields([...auth, 'tokenPath'], 'Token route', { placeholder: 'e.g. /oauth/token', hint: 'Relative route used to exchange credentials for a token. Match the provider authentication API.' })}{fields([...auth, 'managedTokenBaseUrlField'], 'Token base URL input', { placeholder: 'e.g. tokenBaseUrl', hint: 'Setup field name whose value supplies the token server base URL. Declare the same name in Setup inputs.' })}
          <div className="pl-two">{fields([...auth, 'tokenJsonPointer'], 'Token JSON pointer', { placeholder: 'e.g. /access_token', hint: 'Path to the token in the authentication response, not in a data record.' })}{fields([...auth, 'relativeExpiryJsonPointer'], 'Relative expiry JSON pointer', { placeholder: 'e.g. /expires_in', hint: 'Path to the token lifetime in seconds in that same response.' })}</div>
          <ArrayRows document={document} path={[...auth, 'credentialFields']} label="Token credentials" hint="Maps each token-request form field to a setup input containing a stored secret reference, not the credential itself." edit={edit} disabled={disabled} columns={[{ key: 'name', label: 'Form field', placeholder: 'e.g. client_id' }, { key: 'secretRefField', label: 'Credential reference input', placeholder: 'e.g. clientIdSecretRef' }]} newValue={{ name: '', secretRefField: '' }} />
        </>}
        <div className="pl-two">{fields([...h, 'method'], 'Read method', { options: options(['GET', 'POST']), hint: 'Use the provider read-only listing method. POST must not be a write operation.' })}{fields([...h, 'path'], 'Relative read route', { placeholder: 'e.g. /records', hint: 'Added to the provider base URL. Example: /records, not a full URL. Use the actual listing endpoint.' })}</div>
        {fields([...h, 'mapping', 'recordsJsonPointer'], 'Collection JSON pointer', { placeholder: 'e.g. /records', hint: 'Path to the record array in the full API response. For {"records":[...]} use /records. Leave empty only for a root array: [...].' })}
        {fields([...c, 'protectedResource', 'resourceIdField'], 'Authorized account input', { placeholder: 'accountId', hint: 'Setup field name for the authorized source account. Its actual account ID is entered when connecting the Brain.' })}
        <ArrayRows document={document} path={[...h, 'trustedResourcePlacements']} label="Authorized account placement" hint="Where the connector places the authorized account in the request. Example: QUERY + accountId sends ?accountId=<authorized value>." edit={edit} disabled={disabled} columns={[{ key: 'target', label: 'Target', placeholder: 'QUERY', hint: 'QUERY, HEADER or PATH. BODY also requires jsonPointer in Source.' }, { key: 'field', label: 'Request field', placeholder: 'e.g. accountId', hint: 'Exact query/header name, or path placeholder name, expected by the API.' }]} newValue={{ target: 'QUERY', field: '' }} />
        {fields([...h, 'pagination', 'strategy'], 'Pagination', { options: [{ value: 'NONE', label: 'No pagination - one response' }, { value: 'PAGE_SIZE', label: 'Page number and page size' }, { value: 'CURSOR', label: 'Next-page cursor' }], hint: 'Match how the API returns all records. No pagination is suitable only when one response contains the complete collection.' })}
        {textAt(document, [...h, 'pagination', 'strategy']) === 'PAGE_SIZE' && <div className="pl-two">{fields([...h, 'pagination', 'pageQuery'], 'Page query field', { placeholder: 'e.g. page', hint: 'Query parameter containing the page number.' })}{fields([...h, 'pagination', 'sizeQuery'], 'Page size query field', { placeholder: 'e.g. pageSize', hint: 'Query parameter containing the requested records per page.' })}{fields([...h, 'pagination', 'startPage'], 'First page', { numeric: true, placeholder: 'e.g. 1', hint: 'Use 0 or 1 according to the provider documentation.' })}{fields([...h, 'pagination', 'pageSize'], 'Page size', { numeric: true, placeholder: 'e.g. 100', hint: 'Records requested per page. Keep within the provider limit.' })}</div>}
        {textAt(document, [...h, 'pagination', 'strategy']) === 'CURSOR' && <div className="pl-two">{fields([...h, 'pagination', 'cursorQuery'], 'Cursor query field', { placeholder: 'e.g. cursor', hint: 'Query parameter used to send the next-page token.' })}{fields([...h, 'pagination', 'nextCursorJsonPointer'], 'Next cursor JSON pointer', { placeholder: 'e.g. /nextCursor', hint: 'Path to the next-page token in the full API response.' })}</div>}
        {fields([...h, 'pagination', 'maxPages'], 'Maximum pages', { numeric: true, hint: 'Starter: 100 pages per sync. A safety bound, not a desired page count; hitting it does not prove a complete snapshot.' })}
      </>}
      {documents && <>{fields([...d, 'sourceConnector', 'bindingRefField'], 'Document storage connection input', { placeholder: 'documentStorageBindingRef', hint: 'Setup field name, not a bucket URL or access key. The installer selects an existing hosted or BYO storage connection.' })}<div className="pl-notice">Read-only bound S3-compatible storage. Files remain in the source system.</div><StringList document={document} path={key(['allowedMediaTypes'])} label="Allowed media types" hint="Accepted MIME types. Starters: text/plain and application/json; these must match supported document formats." placeholder="e.g. text/plain" edit={edit} disabled={disabled} /><StringList document={document} path={key(['allowedExtensions'])} label="Allowed file extensions" hint="Accepted filename endings, including the leading dot. Starters: .txt and .json." placeholder="e.g. .txt" edit={edit} disabled={disabled} />{fields(key(['maxSourceBytes']), 'Maximum file bytes', { numeric: true, hint: 'Starter: 1048576 bytes = 1 MiB per file. Larger files are not accepted for indexing.' })}</>}
      {sql && <>{fields([...c, 'connectionRefField'], 'SQL connection reference input')}{fields([...c, 'query'], 'Read query', { multiline: true })}<div className="pl-notice pl-warning">This imported SQL definition is retained. Reusable private versions require a qualified customer SQL adapter, which is not available.</div></>}
      {!http && !documents && !sql && <div className="pl-notice pl-warning">This source contract is available in Source. It has not been replaced.</div>}
    </>
  } else if (step === 2) {
    content = http ? <>
      <details><summary>Example API response and field paths</summary><pre className="pl-example">{'{"records": [{\n  "id": "article-42",\n  "accountId": "account-7",\n  "title": "Delivery options",\n  "description": "Delivery is available...",\n  "url": "https://example.com/help/delivery"\n}]}'}</pre><p className="pl-field-help">Collection: /records. Within each record: ID /id, ownership /accountId, searchable text /description. This is an example only; no mappings are added automatically.</p></details>
      {fields([...h, 'mapping', 'idJsonPointer'], 'Stable record ID JSON pointer', { placeholder: 'e.g. /id', hint: 'Path within each record to its unique, stable ID. The same ID lets later syncs update the existing record instead of creating duplicates.' })}
      {fields([...h, 'mapping', 'resourceJsonPointer'], 'Account ownership JSON pointer', { placeholder: 'e.g. /accountId', hint: 'Path within each record to the source account ID. It must agree with the authorized account supplied at installation; it does not grant access.' })}
      <PointerMap document={document} path={[...h, 'mapping', 'contentFields']} label="Searchable content" hint="Text the AI can search and use as evidence. Example: /description becomes description. Paths are relative to one record, not the full response." edit={edit} disabled={disabled} />
      <PointerMap document={document} path={[...h, 'mapping', 'entityFields']} label="Record fields" hint="Additional structured values to retain with the record. Example: /title becomes title. Field names must match the plugin entity configuration." placeholder="e.g. /title" edit={edit} disabled={disabled} />
      <PointerMap document={document} path={[...h, 'mapping', 'metadataFields']} label="Metadata, citations and media" hint="Supporting values, such as a source link: /url becomes sourceUrl. These do not automatically become searchable text or create a custom result renderer." placeholder="e.g. /url" edit={edit} disabled={disabled} />
      <div className="pl-notice">Customer and deployment ownership come from the authorized installation, not editable record fields.</div>
    </> : documents ? <>
      <StringList document={document} path={key(['jsonContentKeys'])} label="JSON content fields" hint="Names of fields holding text inside JSON files. Starters: content, text and body. These are field names, not /JSON/pointers." placeholder="e.g. content" edit={edit} disabled={disabled} />
      <StringList document={document} path={key(['allowedMetadataKeys'])} label="Allowed document metadata" hint="Document metadata keys that may be retained, for example originalFilename or locale. They do not grant ownership or access." placeholder="e.g. locale" edit={edit} disabled={disabled} />
      {fields(key(['maxChunkCharacters']), 'Maximum characters per chunk', { numeric: true, hint: 'Starter: 8000 characters per indexed chunk. Longer text is split within the document policy limits.' })}
      <div className="pl-notice">Discover, preview, approve, then index. Source files and source approval stay in Knowledge.</div>
    </> : sql ? <>{fields([...c, 'query'], 'Read query', { multiline: true })}<div className="pl-notice pl-warning">SQL source configuration is retained, but a qualified customer adapter is required before creating a reusable private version.</div></> : <div className="pl-notice pl-warning">This imported source contract is retained in Source. No qualified customer adapter is available here.</div>
  } else if (step === 3) {
    const selectedAudience = sourceIndex >= 0 ? audience(document, sourceIndex) : 'custom'
    content = <>
      {sourceIndex >= 0 && <label className="pl-field"><span>Who may use this knowledge?</span><select value={selectedAudience} disabled={disabled} onChange={event => edit([{ path: ['contributions', 'knowledgeSources', sourceIndex, 'authModes'], value: audienceModes(event.target.value) }])}>
        {selectedAudience === 'custom' && <option value="custom">Custom access modes</option>}<option value="staff">Internal staff</option><option value="signed">Signed-in customers</option><option value="public">Public visitors / approved content</option>
      </select><small>The plugin definition stays private to your workspace. This setting controls the knowledge access modes; Public visitors allows anonymous use of approved content.</small></label>}
      {http && <>{fields([...h, 'scheduleSeconds'], 'Baseline sync interval (seconds)', { numeric: true, hint: 'Starter: 900 seconds = 15 minutes between scheduled reconciliations after the connection is applied. Saving the definition does not start syncing.' })}
        {fields([...h, 'tombstonePolicy', 'strategy'], 'Deletion handling', { options: [{ value: 'NONE', label: 'No source deletion signals' }, { value: 'ABSENT_FROM_SNAPSHOT', label: 'Absent from a complete snapshot' }, { value: 'FIELD_VALUE', label: 'Explicit deletion field' }, { value: 'ABSENT_OR_FIELD_VALUE', label: 'Complete snapshot or deletion field' }], hint: 'Controls removal of indexed records, not deletion in the source API. Starter: keep records unless a supported deletion strategy is configured.' })}
        {['FIELD_VALUE', 'ABSENT_OR_FIELD_VALUE'].includes(textAt(document, [...h, 'tombstonePolicy', 'strategy'])) && <>{fields([...h, 'tombstonePolicy', 'operationJsonPointer'], 'Deletion field JSON pointer', { placeholder: 'e.g. /status', hint: 'Path within a record containing its deletion marker.' })}<StringList document={document} path={[...h, 'tombstonePolicy', 'deleteValues']} label="Deletion values" hint="Exact provider values meaning removed, for example deleted. Match the source API contract." placeholder="e.g. deleted" edit={edit} disabled={disabled} /></>}
        <div className="pl-notice">Incomplete pages are not proof of removal. Notifications and targeted reconciliation require a verified adapter contract.</div>
        <details><summary>Retry and rate policy</summary>{fields([...p, 'ratePolicy', 'maxConcurrent'], 'Maximum concurrent requests', { numeric: true, placeholder: 'e.g. 1', hint: 'Optional concurrent-request ceiling, from 1 to 100. Omit to retain connector defaults; choose within the provider rate limit.' })}{fields([...p, 'ratePolicy', 'maxAttempts'], 'Maximum attempts', { numeric: true, placeholder: 'e.g. 2', hint: 'Optional total attempts per request, including the first attempt, from 1 to 5.' })}{fields([...p, 'ratePolicy', 'retryBackoffMs'], 'Retry backoff (milliseconds)', { numeric: true, placeholder: 'e.g. 1000', hint: 'Optional delay between retry attempts, from 0 to 30000 ms. 1000 ms = 1 second.' })}</details>
      </>}
      {documents && <>{check(key(['initialIndexRequiresConfirmation']), 'Require approval before initial indexing', 'Starter: on. The supported private document contract requires explicit initial approval.')}{check(key(['trustedAutoIndexingAllowed']), 'Allow trusted automatic indexing', 'Starter: off. Keep off for the supported private document contract; changing this is not an approval bypass.')}<div className="pl-two">{fields(key(['evidenceRetentionDays']), 'Evidence retention (days)', { numeric: true, hint: 'Starter: 30 days for retained document evidence records. This does not delete the original S3 files.' })}{fields(key(['commandRetentionDays']), 'Command retention (days)', { numeric: true, hint: 'Starter: 30 days for document-processing command history, not source-file storage.' })}</div><div className="pl-notice">Replacement removes derived evidence, not source files. Public content still requires the existing approval policy.</div></>}
      {sql && <div className="pl-notice pl-warning">Customer SQL synchronization is not available. Source settings are retained without enabling a connection.</div>}
    </>
  } else if (step === 4) content = <SetupInputs document={document} edit={edit} disabled={disabled} issues={issues} />
  else content = <>
    <dl className="pl-pairs">{[
      ['Name', textAt(document, ['displayName']) || 'Not named'], ['Purpose', textAt(document, ['description']) || 'Not set'],
      ['Source contract', sourceNames[kind as SourceKind] || kind || 'Not set'], ['Datasets', String(datasetList(document).length)],
      ['Audience', sourceIndex < 0 ? 'No matching knowledge source' : { public: 'Public visitors / approved content', signed: 'Signed-in customers', staff: 'Internal staff', custom: 'Custom access modes' }[audience(document, sourceIndex)]],
      ['Availability', 'Private to your workspace'],
    ].map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}</dl>
    {fields(['version'], 'Reusable version', { placeholder: '1.0.0', hint: 'Starter: 1.0.0. Use a new version for later changes, for example 1.0.1; an existing saved version cannot be overwritten.' })}
    {sql && <div className="pl-notice pl-warning">A qualified customer SQL adapter is required before this definition can become a reusable private version.</div>}
    <div className="pl-notice">Saving a version does not install it, index data or change a running Smart Brain.</div>
  </>
  return <>{content}</>
}

function ArrayRows({ document, path, label, hint, columns, newValue, edit, disabled }: { document: ExactObject; path: DocumentPath; label: string; hint?: string; columns: ({ key: string; label: string } & Partial<FieldProps>)[]; newValue: ExactObject; edit: EditDocument; disabled: boolean }) {
  const value = at(document, path)
  if (value !== undefined && !Array.isArray(value)) return <div className="pl-notice pl-warning">{label} has a custom source value. Edit it in Source.</div>
  const rows = Array.isArray(value) ? value : []
  return <section className="pl-array"><h3>{label}</h3>{hint && <p className="pl-field-help">{hint}</p>}{rows.map((_, index) => <div className="pl-input-row" key={index}>{columns.map(({ key, ...props }) => <Field key={key} {...props} document={document} path={[...path, index, key]} edit={edit} disabled={disabled} />)}<button className="pl-icon-button" title={`Remove ${label} ${index + 1}`} aria-label={`Remove ${label} ${index + 1}`} disabled={disabled} onClick={() => edit([{ path: [...path, index], value: undefined, remove: true }])}><DeleteOutline /></button></div>)}<button className="pl-text-button" disabled={disabled} onClick={() => edit([{ path: [...path, rows.length], value: newValue }])}><AddOutlined />Add {label.toLowerCase()}</button></section>
}
function StringList({ document, path, label, edit, disabled, hint, placeholder }: Omit<FieldProps, 'issues'>) {
  const value = at(document, path)
  if (value !== undefined && !Array.isArray(value)) return <div className="pl-notice pl-warning">{label} has a custom source value. Edit it in Source.</div>
  const rows = Array.isArray(value) ? value : []
  return <section className="pl-array"><h3>{label}</h3>{hint && <p className="pl-field-help">{hint}</p>}{rows.map((_, index) => <div className="pl-input-row pl-string-row" key={index}><Field document={document} path={[...path, index]} label={`${label} ${index + 1}`} placeholder={placeholder} edit={edit} disabled={disabled} /><button className="pl-icon-button" title={`Remove ${label} ${index + 1}`} aria-label={`Remove ${label} ${index + 1}`} disabled={disabled} onClick={() => edit([{ path: [...path, index], value: undefined, remove: true }])}><DeleteOutline /></button></div>)}<button className="pl-text-button" disabled={disabled} onClick={() => edit([{ path: [...path, rows.length], value: '' }])}><AddOutlined />Add value</button></section>
}
function SetupInputs({ document, edit, disabled, issues }: { document: ExactObject; edit: EditDocument; disabled: boolean; issues: PluginIssue[] }) {
  const value = at(document, ['installForm'])
  if (value !== undefined && !Array.isArray(value)) return <div className="pl-notice pl-warning">Setup inputs have a custom source value. Edit them in Source.</div>
  const rows = Array.isArray(value) ? value : []
  return <><div className="pl-notice">These inputs become the connection form for a Smart Brain. Their identifiers must match the setup field names in Source contract. Actual URLs, account IDs and secret references are entered at installation, not here.</div>
    {rows.map((row, index) => {
      const path: DocumentPath = ['installForm', index]
      const field = (key: string, label: string, props: Partial<FieldProps> = {}) => <Field document={document} path={[...path, key]} label={label} edit={edit} disabled={disabled} issues={issues} {...props} />
      return <section className="pl-setup-row" key={index}><div className="pl-row pl-spaced"><h3>{textAt(row, ['label']) || `Input ${index + 1}`}</h3><button className="pl-icon-button" aria-label={`Remove input ${index + 1}`} title="Remove input" disabled={disabled} onClick={() => edit([{ path, value: undefined, remove: true }])}><DeleteOutline /></button></div><div className="pl-two">{field('id', 'Input identifier', { placeholder: 'e.g. providerBaseUrl', hint: 'Exact setup field name referenced by the source contract, not its connection value.' })}{field('label', 'Installer label', { placeholder: 'e.g. Help centre API URL', hint: 'The human-readable label shown when connecting a Smart Brain.' })}{field('type', 'Type', { options: [{ value: 'text', label: 'Text' }, { value: 'url', label: 'Web address (URL)' }, { value: 'boolean', label: 'On / off' }, { value: 'select', label: 'Choice list' }, { value: 'number', label: 'Number' }, { value: 'secretRef', label: 'Stored secret reference' }], hint: 'Determines the installation control. A secret reference points to a stored Platform credential.' })}<CheckField document={document} path={[...path, 'required']} label="Required" hint="The installer must supply this value before saving the connection." edit={edit} disabled={disabled} /></div>{field('description', 'Help text', { placeholder: 'e.g. Base URL from your provider, such as https://api.example.com.', hint: 'Displayed below this input in the Brain connection form. Include the expected format or a safe example.' })}
        {textAt(row, ['type']) === 'select' && <StringList document={document} path={[...path, 'options']} label="Options" hint="Exact values offered to the installer. These must be values your source contract accepts." placeholder="e.g. en-GB" edit={edit} disabled={disabled} />}
      </section>
    })}<button className="pl-text-button" disabled={disabled} onClick={() => edit([{ path: ['installForm', rows.length], value: { id: '', label: '', type: 'text', required: false } }])}><AddOutlined />Add setup input</button>
  </>
}
