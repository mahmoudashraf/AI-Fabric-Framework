import { createContext, useContext, useId, type ReactNode } from 'react'
import AddOutlined from '@mui/icons-material/AddOutlined'
import DeleteOutline from '@mui/icons-material/DeleteOutline'
import { at, audience, audienceModes, datasetList, datasetPath, knowledgeIndex, numericValue, objectValue, sourceKind, sourceNames, textAt, type DocumentPath, type ExactObject, type SourceKind } from './creatorDocument'
import type { PluginIssue } from './types'

export type EditDocument = (edits: { path: DocumentPath; value: unknown; remove?: boolean }[]) => void
export const NumericDraftContext = createContext<{ drafts: Record<string, string>; setDraft: (path: string, value: string | undefined) => void }>({ drafts: {}, setDraft: () => {} })
type FieldProps = {
  document: ExactObject; path: DocumentPath; label: string; edit: EditDocument; disabled?: boolean
  options?: { value: string; label: string }[]; multiline?: boolean; numeric?: boolean; hint?: string; issues?: PluginIssue[]
}
function issuePath(path: DocumentPath) { return '/' + path.map(key => String(key).replace(/~/g, '~0').replace(/\//g, '~1')).join('/') }
export function Field({ document, path, label, edit, disabled, options, multiline, numeric, hint, issues }: FieldProps) {
  const id = useId()
  const numbers = useContext(NumericDraftContext)
  const draftKey = issuePath(path)
  const pending = numbers.drafts[draftKey]
  const value = pending ?? textAt(document, path)
  const raw = at(document, path)
  const fieldIssue = issues?.find(issue => issue.path === issuePath(path) || issue.path === path.join('.'))
  const error = pending !== undefined ? 'Incomplete number. Finish this value before saving or checking.' : fieldIssue?.message
  const change = (next: string) => {
    let field: unknown = next
    if (numeric) {
      try { field = numericValue(next) }
      catch { numbers.setDraft(draftKey, next); return }
    }
    edit([{ path, value: field }])
    if (numeric) numbers.setDraft(draftKey, undefined)
  }
  return <label className="pl-field" htmlFor={id}><span>{label}</span>
    {options ? <select id={id} value={value} disabled={disabled} onChange={event => change(event.target.value)} aria-invalid={Boolean(error)} aria-describedby={error || hint ? `${id}-help` : undefined}>
      {!options.some(option => option.value === value) && <option value={value}>{value || (raw === null ? 'Explicit null' : 'Not set')}</option>}
      {options.map(option => <option key={option.value} value={option.value}>{option.label}</option>)}
    </select> : multiline ? <textarea id={id} rows={3} value={value} disabled={disabled} onChange={event => change(event.target.value)} aria-invalid={Boolean(error)} />
      : <input id={id} value={value} disabled={disabled} inputMode={numeric ? 'decimal' : undefined} onChange={event => change(event.target.value)} aria-invalid={Boolean(error)} aria-describedby={error || hint ? `${id}-help` : undefined} />}
    {(error || hint || raw === null) && <small id={`${id}-help`} className={error ? 'pl-error-text' : undefined}>{error || hint || 'Explicit null'}</small>}
  </label>
}
function CheckField({ document, path, label, edit, disabled }: Omit<FieldProps, 'options'>) {
  const value = at(document, path)
  return <label className="pl-checkbox"><input type="checkbox" checked={value === true} disabled={disabled} onChange={event => edit([{ path, value: event.target.checked }])} />{label}{value !== true && value !== false && value !== undefined && <span className="pl-caption">Custom source value retained</span>}</label>
}
const options = (values: string[]) => values.map(value => ({ value, label: value }))

function PointerMap({ document, path, label, edit, disabled }: Omit<FieldProps, 'issues'>) {
  const value = at(document, path)
  if (value !== undefined && !objectValue(value)) return <div className="pl-notice pl-warning">{label} has a custom source value. Edit it in Source.</div>
  const entries = Object.entries(objectValue(value) ? value : {})
  return <section className="pl-mapping-section"><h3>{label}</h3><div className="pl-table-scroll"><table className="pl-mapping"><thead><tr><th>Source field</th><th>Meaning</th><th><span className="pl-sr-only">Remove</span></th></tr></thead><tbody>
    {entries.map(([key, pointer]) => <tr key={key}><td><input aria-label={`${label}: ${key} source field`} value={typeof pointer === 'string' ? pointer : ''} disabled={disabled} onChange={event => edit([{ path: [...path, key], value: event.target.value }])} /></td>
      <td><input aria-label={`${label}: ${key} meaning`} defaultValue={key} disabled={disabled} onBlur={event => {
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
  const check = (path: DocumentPath, label: string) => <CheckField document={document} path={path} label={label} edit={edit} disabled={disabled} />
  const key = (suffix: DocumentPath) => [...d, 'documentPolicy', ...suffix]
  let content: ReactNode
  if (step === 0) return <>
    {fields(['displayName'], 'Display name')}{fields(['description'], 'Purpose', { multiline: true })}
    {fields(['category'], 'Content category')}
    <details><summary>Technical identity</summary>{fields(['pluginId'], 'Plugin identifier', { disabled: disabled || immutableId })}<Field document={document} path={[...d, 'datasetId']} label="Dataset identifier" edit={edits => {
      const previous = textAt(document, [...d, 'datasetId'])
      const sources = at(document, ['contributions', 'knowledgeSources'])
      if (Array.isArray(sources)) sources.forEach((source, index) => {
        if (textAt(source, ['datasetRef']) === previous) edits.push({ path: ['contributions', 'knowledgeSources', index, 'datasetRef'], value: edits[0].value })
      })
      edit(edits)
    }} disabled={disabled} />{fields([...d, 'entityType'], 'Record type')}</details>
  </>
  if (step === 1) {
    content = <><div className="pl-source-choices" role="group" aria-label="Source contract">{(['HTTP_JSON', 'S3_COMPATIBLE_OBJECT_STORAGE'] as SourceKind[]).map(option => <button key={option} type="button" className={kind === option ? 'pl-selected' : ''} aria-pressed={kind === option} disabled={disabled} onClick={() => option !== kind && onSourceChange(option)}><strong>{sourceNames[option]}</strong></button>)}</div>
      <details open={sql || undefined}><summary>Advanced source contracts</summary><button type="button" disabled aria-pressed={sql}><strong>SQL connection</strong><small>Requires a qualified customer adapter</small></button></details>
      {http && <>
        {fields([...p, 'managedBaseUrlField'], 'Provider base URL input')}{fields([...p, 'managedAllowedHostField'], 'Allowed host input')}
        {fields([...auth, 'strategy'], 'Authentication', { options: [{ value: 'API_KEY', label: 'API key reference' }, { value: 'FORM_TOKEN_EXCHANGE', label: 'Form token exchange' }] })}
        {textAt(document, [...auth, 'strategy']) === 'API_KEY' ? <div className="pl-two">{fields([...auth, 'apiKeyHeader'], 'API key header')}{fields([...auth, 'apiKeySecretRefField'], 'Credential reference input')}</div> : <>
          {fields([...auth, 'tokenPath'], 'Token route')}{fields([...auth, 'managedTokenBaseUrlField'], 'Token base URL input')}
          <div className="pl-two">{fields([...auth, 'tokenJsonPointer'], 'Token JSON pointer')}{fields([...auth, 'relativeExpiryJsonPointer'], 'Relative expiry JSON pointer')}</div>
          <ArrayRows document={document} path={[...auth, 'credentialFields']} label="Token credentials" edit={edit} disabled={disabled} columns={[['name', 'Form field'], ['secretRefField', 'Credential reference input']]} newValue={{ name: '', secretRefField: '' }} />
        </>}
        <div className="pl-two">{fields([...h, 'method'], 'Read method', { options: options(['GET', 'POST']) })}{fields([...h, 'path'], 'Relative read route')}</div>
        {fields([...h, 'mapping', 'recordsJsonPointer'], 'Collection JSON pointer')}
        {fields([...c, 'protectedResource', 'resourceIdField'], 'Authorized account input')}
        <ArrayRows document={document} path={[...h, 'trustedResourcePlacements']} label="Authorized account placement" edit={edit} disabled={disabled} columns={[['target', 'Target'], ['field', 'Request field']]} newValue={{ target: 'QUERY', field: '' }} />
        {fields([...h, 'pagination', 'strategy'], 'Pagination', { options: options(['NONE', 'PAGE_SIZE', 'CURSOR']) })}
        {textAt(document, [...h, 'pagination', 'strategy']) === 'PAGE_SIZE' && <div className="pl-two">{fields([...h, 'pagination', 'pageQuery'], 'Page query field')}{fields([...h, 'pagination', 'sizeQuery'], 'Page size query field')}{fields([...h, 'pagination', 'startPage'], 'First page', { numeric: true })}{fields([...h, 'pagination', 'pageSize'], 'Page size', { numeric: true })}</div>}
        {textAt(document, [...h, 'pagination', 'strategy']) === 'CURSOR' && <div className="pl-two">{fields([...h, 'pagination', 'cursorQuery'], 'Cursor query field')}{fields([...h, 'pagination', 'nextCursorJsonPointer'], 'Next cursor JSON pointer')}</div>}
        {fields([...h, 'pagination', 'maxPages'], 'Maximum pages', { numeric: true })}
      </>}
      {documents && <>{fields([...d, 'sourceConnector', 'bindingRefField'], 'Document storage connection input')}<div className="pl-notice">Read-only bound S3-compatible storage. Files remain in the source system.</div><StringList document={document} path={key(['allowedMediaTypes'])} label="Allowed media types" edit={edit} disabled={disabled} /><StringList document={document} path={key(['allowedExtensions'])} label="Allowed file extensions" edit={edit} disabled={disabled} />{fields(key(['maxSourceBytes']), 'Maximum file bytes', { numeric: true })}</>}
      {sql && <>{fields([...c, 'connectionRefField'], 'SQL connection reference input')}{fields([...c, 'query'], 'Read query', { multiline: true })}<div className="pl-notice pl-warning">This imported SQL definition is retained. Reusable private versions require a qualified customer SQL adapter, which is not available.</div></>}
      {!http && !documents && !sql && <div className="pl-notice pl-warning">This source contract is available in Source. It has not been replaced.</div>}
    </>
  } else if (step === 2) {
    content = http ? <>
      {fields([...h, 'mapping', 'idJsonPointer'], 'Stable record ID JSON pointer')}
      {fields([...h, 'mapping', 'resourceJsonPointer'], 'Account ownership JSON pointer')}
      <PointerMap document={document} path={[...h, 'mapping', 'contentFields']} label="Searchable content" edit={edit} disabled={disabled} />
      <PointerMap document={document} path={[...h, 'mapping', 'entityFields']} label="Record fields" edit={edit} disabled={disabled} />
      <PointerMap document={document} path={[...h, 'mapping', 'metadataFields']} label="Metadata, citations and media" edit={edit} disabled={disabled} />
      <div className="pl-notice">Customer and deployment ownership come from the authorized installation, not editable record fields.</div>
    </> : documents ? <>
      <StringList document={document} path={key(['jsonContentKeys'])} label="JSON content fields" edit={edit} disabled={disabled} />
      <StringList document={document} path={key(['allowedMetadataKeys'])} label="Allowed document metadata" edit={edit} disabled={disabled} />
      {fields(key(['maxChunkCharacters']), 'Maximum characters per chunk', { numeric: true })}
      <div className="pl-notice">Discover, preview, approve, then index. Source files and source approval stay in Knowledge.</div>
    </> : sql ? <>{fields([...c, 'query'], 'Read query', { multiline: true })}<div className="pl-notice pl-warning">SQL source configuration is retained, but a qualified customer adapter is required before creating a reusable private version.</div></> : <div className="pl-notice pl-warning">This imported source contract is retained in Source. No qualified customer adapter is available here.</div>
  } else if (step === 3) {
    const selectedAudience = sourceIndex >= 0 ? audience(document, sourceIndex) : 'custom'
    content = <>
      {sourceIndex >= 0 && <label className="pl-field"><span>Who may use this knowledge?</span><select value={selectedAudience} disabled={disabled} onChange={event => edit([{ path: ['contributions', 'knowledgeSources', sourceIndex, 'authModes'], value: audienceModes(event.target.value) }])}>
        {selectedAudience === 'custom' && <option value="custom">Custom access modes</option>}<option value="staff">Internal staff</option><option value="signed">Signed-in customers</option><option value="public">Public visitors / approved content</option>
      </select></label>}
      {http && <>{fields([...h, 'scheduleSeconds'], 'Baseline sync interval (seconds)', { numeric: true })}
        {fields([...h, 'tombstonePolicy', 'strategy'], 'Deletion handling', { options: [{ value: 'NONE', label: 'No source deletion signals' }, { value: 'ABSENT_FROM_SNAPSHOT', label: 'Absent from a complete snapshot' }, { value: 'FIELD_VALUE', label: 'Explicit deletion field' }, { value: 'ABSENT_OR_FIELD_VALUE', label: 'Complete snapshot or deletion field' }] })}
        {['FIELD_VALUE', 'ABSENT_OR_FIELD_VALUE'].includes(textAt(document, [...h, 'tombstonePolicy', 'strategy'])) && <>{fields([...h, 'tombstonePolicy', 'operationJsonPointer'], 'Deletion field JSON pointer')}<StringList document={document} path={[...h, 'tombstonePolicy', 'deleteValues']} label="Deletion values" edit={edit} disabled={disabled} /></>}
        <div className="pl-notice">Incomplete pages are not proof of removal. Notifications and targeted reconciliation require a verified adapter contract.</div>
        <details><summary>Retry and rate policy</summary>{fields([...p, 'ratePolicy', 'maxConcurrent'], 'Maximum concurrent requests', { numeric: true })}{fields([...p, 'ratePolicy', 'maxAttempts'], 'Maximum attempts', { numeric: true })}{fields([...p, 'ratePolicy', 'retryBackoffMs'], 'Retry backoff (milliseconds)', { numeric: true })}</details>
      </>}
      {documents && <>{check(key(['initialIndexRequiresConfirmation']), 'Require approval before initial indexing')}{check(key(['trustedAutoIndexingAllowed']), 'Allow trusted automatic indexing')}<div className="pl-two">{fields(key(['evidenceRetentionDays']), 'Evidence retention (days)', { numeric: true })}{fields(key(['commandRetentionDays']), 'Command retention (days)', { numeric: true })}</div><div className="pl-notice">Replacement removes derived evidence, not source files. Public content still requires the existing approval policy.</div></>}
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
    {fields(['version'], 'Reusable version')}
    {sql && <div className="pl-notice pl-warning">A qualified customer SQL adapter is required before this definition can become a reusable private version.</div>}
    <div className="pl-notice">Saving a version does not install it, index data or change a running Smart Brain.</div>
  </>
  return <>{content}</>
}

function ArrayRows({ document, path, label, columns, newValue, edit, disabled }: { document: ExactObject; path: DocumentPath; label: string; columns: [string, string][]; newValue: ExactObject; edit: EditDocument; disabled: boolean }) {
  const value = at(document, path)
  if (value !== undefined && !Array.isArray(value)) return <div className="pl-notice pl-warning">{label} has a custom source value. Edit it in Source.</div>
  const rows = Array.isArray(value) ? value : []
  return <section className="pl-array"><h3>{label}</h3>{rows.map((row, index) => <div className="pl-input-row" key={index}>{columns.map(([key, name]) => <Field key={key} document={document} path={[...path, index, key]} label={name} edit={edit} disabled={disabled} />)}<button className="pl-icon-button" title={`Remove ${label} ${index + 1}`} aria-label={`Remove ${label} ${index + 1}`} disabled={disabled} onClick={() => edit([{ path: [...path, index], value: undefined, remove: true }])}><DeleteOutline /></button></div>)}<button className="pl-text-button" disabled={disabled} onClick={() => edit([{ path: [...path, rows.length], value: newValue }])}><AddOutlined />Add {label.toLowerCase()}</button></section>
}
function StringList({ document, path, label, edit, disabled }: Omit<FieldProps, 'issues'>) {
  const value = at(document, path)
  if (value !== undefined && !Array.isArray(value)) return <div className="pl-notice pl-warning">{label} has a custom source value. Edit it in Source.</div>
  const rows = Array.isArray(value) ? value : []
  return <section className="pl-array"><h3>{label}</h3>{rows.map((_, index) => <div className="pl-input-row pl-string-row" key={index}><Field document={document} path={[...path, index]} label={`${label} ${index + 1}`} edit={edit} disabled={disabled} /><button className="pl-icon-button" title={`Remove ${label} ${index + 1}`} aria-label={`Remove ${label} ${index + 1}`} disabled={disabled} onClick={() => edit([{ path: [...path, index], value: undefined, remove: true }])}><DeleteOutline /></button></div>)}<button className="pl-text-button" disabled={disabled} onClick={() => edit([{ path: [...path, rows.length], value: '' }])}><AddOutlined />Add value</button></section>
}
function SetupInputs({ document, edit, disabled, issues }: { document: ExactObject; edit: EditDocument; disabled: boolean; issues: PluginIssue[] }) {
  const value = at(document, ['installForm'])
  if (value !== undefined && !Array.isArray(value)) return <div className="pl-notice pl-warning">Setup inputs have a custom source value. Edit them in Source.</div>
  const rows = Array.isArray(value) ? value : []
  return <><div className="pl-notice">Definition inputs describe slots. Credentials are supplied as references when connecting a Brain.</div>
    {rows.map((row, index) => {
      const path: DocumentPath = ['installForm', index]
      const field = (key: string, label: string, props: Partial<FieldProps> = {}) => <Field document={document} path={[...path, key]} label={label} edit={edit} disabled={disabled} issues={issues} {...props} />
      return <section className="pl-setup-row" key={index}><div className="pl-row pl-spaced"><h3>{textAt(row, ['label']) || `Input ${index + 1}`}</h3><button className="pl-icon-button" aria-label={`Remove input ${index + 1}`} title="Remove input" disabled={disabled} onClick={() => edit([{ path, value: undefined, remove: true }])}><DeleteOutline /></button></div><div className="pl-two">{field('id', 'Input identifier')}{field('label', 'Installer label')}{field('type', 'Type', { options: options(['text', 'url', 'boolean', 'select', 'number', 'secretref']) })}<CheckField document={document} path={[...path, 'required']} label="Required" edit={edit} disabled={disabled} /></div>{field('description', 'Help text')}
        {textAt(row, ['type']) === 'select' && <StringList document={document} path={[...path, 'options']} label="Options" edit={edit} disabled={disabled} />}
      </section>
    })}<button className="pl-text-button" disabled={disabled} onClick={() => edit([{ path: ['installForm', rows.length], value: { id: '', label: '', type: 'text', required: false } }])}><AddOutlined />Add setup input</button>
  </>
}
