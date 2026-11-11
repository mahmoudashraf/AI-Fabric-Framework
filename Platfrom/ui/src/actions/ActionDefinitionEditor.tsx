import { useEffect, useMemo, useState } from 'react'
import AddRounded from '@mui/icons-material/AddRounded'
import CodeRounded from '@mui/icons-material/CodeRounded'
import DeleteOutlineRounded from '@mui/icons-material/DeleteOutlineRounded'
import RouteRounded from '@mui/icons-material/RouteRounded'
import ClearRounded from '@mui/icons-material/ClearRounded'
import {
  Alert, Autocomplete, Box, Button, Checkbox, Dialog, DialogActions, DialogContent,
  DialogTitle, Divider, FormControlLabel, IconButton, InputAdornment, MenuItem, Stack, TextField, Tooltip, Typography,
} from '@mui/material'
import { ConfigurationDetails } from '../components/ConfigurationSurface'
import { parseExactObject, stringifyExactJson } from '../lib/losslessJson'
import { useOptionalBrainWorkspace } from '../brain/BrainWorkspaceContext'
import { McpActionDestination } from '../mcp/McpActionDestination'
import { mcpActionDestination, mcpLocalSettings, replaceMcpLocalSettings } from '../mcp/actionConfiguration'
import {
  ACTION_ACCESS_MODES, ACTION_PARAM_SOURCES, ACTION_PARAM_TYPES, ACTION_RENAME_MESSAGE, ACTION_RESULT_HINTS,
  actionNameError, actionRemovalBlock, appendActionDefinition, appendActionParameter,
  isJsonObject, patchActionDefinition, patchActionParameter, readActionCatalogue, readActionParams,
  removeActionDefinition, removeActionParameter, replaceActionDefinition, parseActionParameterInteger,
  parseActionParameterJson, type ActionParameterJsonField, type JsonObject,
} from './actionDefinitionModel'

type Props = {
  editorValue: string
  onChange: (value: string) => void
  disabled?: boolean
  initialActionName?: string
  routedActionNames?: string[]
  onEditRoute?: (actionName: string) => void
}

const fields = { display: 'grid', gridTemplateColumns: { xs: 'minmax(0, 1fr)', sm: 'repeat(2, minmax(0, 1fr))' }, gap: 2 }
const text = (record: JsonObject, key: string) => typeof record[key] === 'string' ? record[key] as string : ''

function ParameterIntegerField({ label, value, disabled, onChange }: {
  label: string; value: unknown; disabled: boolean; onChange: (value: unknown) => void
}) {
  const serialized = value === undefined ? '' : stringifyExactJson(value)
  const [input, setInput] = useState(serialized)
  const [error, setError] = useState<string | null>(null)
  useEffect(() => { setInput(serialized); setError(null) }, [serialized])
  return <TextField size="small" label={label} value={input} disabled={disabled} inputProps={{ inputMode: 'numeric' }}
    error={Boolean(error)} helperText={error ?? 'Optional signed integer. Example: 1. Blank removes the bound.'}
    onChange={event => {
      setInput(event.target.value)
      try { onChange(parseActionParameterInteger(event.target.value)); setError(null) }
      catch (error) { setError(error instanceof Error ? error.message : 'Invalid integer. Input is not applied.') }
    }} />
}

const parameterJsonLabels: Record<ActionParameterJsonField, string> = {
  defaultValue: 'Default value', allowedValues: 'Allowed values', resolveFrom: 'Trusted resolver (resolveFrom)',
  items: 'Array items schema', properties: 'Object properties schema', requiredProperties: 'Required properties',
}
const parameterJsonHelp: Record<ActionParameterJsonField, string> = {
  defaultValue: 'Any JSON value, e.g. "OPEN", 10 or {"status":"OPEN"}.',
  allowedValues: 'String array, e.g. ["OPEN", "CLOSED"].',
  resolveFrom: 'Trusted runtime resolver, e.g. {"source":"ATTACHMENT_METADATA","metadataKeys":["lookupValue","id"]}. This does not grant access or bypass evidence rules.',
  items: 'One parameter definition object, e.g. {"type":"STRING"}.',
  properties: 'Named parameter definitions, e.g. {"status":{"type":"STRING","allowedValues":["OPEN","CLOSED"]}}.',
  requiredProperties: 'Required object property names, e.g. ["status"].',
}

export function ActionDefinitionEditor({ editorValue, onChange, disabled = false, initialActionName, routedActionNames = [], onEditRoute }: Props) {
  const brain = useOptionalBrainWorkspace()
  const catalogue = useMemo(() => {
    try { return { ...readActionCatalogue(editorValue), error: null } }
    catch (error) { return { actions: [], root: {}, error: error instanceof Error ? error.message : 'Invalid actions JSON.' } }
  }, [editorValue])
  const [selection, setSelection] = useState({ index: 0, name: initialActionName ?? '' })
  const [adding, setAdding] = useState(false)
  const [newName, setNewName] = useState('')
  const [removing, setRemoving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [jsonEdit, setJsonEdit] = useState<{ source: string; index: number; value: string; paramIndex?: number; field?: ActionParameterJsonField } | null>(null)
  const [jsonError, setJsonError] = useState<string | null>(null)

  const namedIndex = catalogue.actions.findIndex(action => isJsonObject(action) && action.name === selection.name)
  const selectedAtIndex = catalogue.actions[selection.index]
  const index = isJsonObject(selectedAtIndex) && selectedAtIndex.name === selection.name
    ? selection.index : namedIndex >= 0 ? namedIndex : Math.min(selection.index, catalogue.actions.length - 1)
  const candidate = catalogue.actions[index]
  const action = isJsonObject(candidate) ? candidate : null
  const mcp = mcpActionDestination(action)
  const name = action ? text(action, 'name') : ''
  const params = useMemo(() => {
    try { return { values: action ? readActionParams(action) : [], error: null } }
    catch (error) { return { values: [], error: error instanceof Error ? error.message : 'Invalid params array.' } }
  }, [action])

  useEffect(() => { setSelection({ index: 0, name: initialActionName ?? '' }) }, [initialActionName])
  // A refresh/discard must never let a JSON dialog apply an older snapshot over the current buffer.
  const jsonStale = jsonEdit !== null && jsonEdit.source !== editorValue
  const mutate = (operation: () => string) => {
    if (disabled) return
    try { onChange(operation()); setError(null) }
    catch (error) { setError(error instanceof Error ? error.message : 'Unable to edit this action.') }
  }
  const patch = (change: JsonObject) => mutate(() => patchActionDefinition(editorValue, index, change))
  const patchParam = (paramIndex: number, change: JsonObject) => mutate(() => patchActionParameter(editorValue, index, paramIndex, change))
  const removalBlock = catalogue.error ? null : actionRemovalBlock(editorValue, index, routedActionNames)
  const addError = actionNameError(newName, catalogue.actions)
  const grounding = action?.groundingEligible === undefined ? action?.accessMode === 'READ' : action.groundingEligible === true
  const invalidSpaces = action?.groundingVectorSpaces !== undefined && (!Array.isArray(action.groundingVectorSpaces)
    || action.groundingVectorSpaces.some(space => typeof space !== 'string'))
  const parameterJsonControl = (param: JsonObject, paramIndex: number, field: ActionParameterJsonField) => {
    const label = parameterJsonLabels[field]
    return <TextField key={field} fullWidth size="small" multiline maxRows={3} label={label}
      value={param[field] === undefined ? '' : stringifyExactJson(param[field])} disabled={disabled}
      helperText={parameterJsonHelp[field]} InputProps={{ readOnly: true, endAdornment: <InputAdornment position="end">
        <Tooltip title={`Edit ${label}`}><span><IconButton size="small" aria-label={`Edit ${label} for parameter ${paramIndex + 1}`} disabled={disabled} onClick={() => {
          setJsonError(null)
          setJsonEdit({ source: editorValue, index, paramIndex, field,
            value: param[field] === undefined ? field === 'defaultValue' ? 'null' : field === 'allowedValues' || field === 'requiredProperties' ? '[]' : '{}' : stringifyExactJson(param[field], 2) })
        }}><CodeRounded fontSize="small" /></IconButton></span></Tooltip>
        <Tooltip title={`Clear ${label}`}><span><IconButton size="small" aria-label={`Clear ${label} for parameter ${paramIndex + 1}`}
          disabled={disabled || param[field] === undefined} onClick={() => patchParam(paramIndex, { [field]: undefined })}><ClearRounded fontSize="small" /></IconButton></span></Tooltip>
      </InputAdornment> }} />
  }

  return <Stack spacing={2} sx={{ minWidth: 0 }}>
    <Typography variant="h6">Action definitions</Typography>
    {catalogue.error ? <Alert severity="error">{catalogue.error} Repair the current buffer in Advanced catalogue JSON.</Alert> : <>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} alignItems={{ sm: 'center' }}>
        {catalogue.actions.length > 0 ? <TextField select fullWidth size="small" label="Action" value={index}
          onChange={event => {
            const nextIndex = Number(event.target.value)
            const next = catalogue.actions[nextIndex]
            setSelection({ index: nextIndex, name: isJsonObject(next) ? text(next, 'name') : '' })
            setError(null)
          }}>
          {catalogue.actions.map((item, itemIndex) => <MenuItem key={itemIndex} value={itemIndex} sx={{ whiteSpace: 'normal', overflowWrap: 'anywhere' }}>
            {isJsonObject(item) ? text(item, 'displayName') || text(item, 'name') || `Unnamed action ${itemIndex + 1}` : `Invalid action ${itemIndex + 1}`}
          </MenuItem>)}
        </TextField> : null}
        <Stack direction="row" spacing={1} alignItems="center" sx={{ flexShrink: 0 }}>
          <Button startIcon={<AddRounded />} variant="outlined" disabled={disabled} onClick={() => { setNewName(''); setAdding(true) }}>Add action</Button>
          {catalogue.actions.length > 0 ? <Tooltip title="Remove selected action"><span><IconButton aria-label="Remove selected action" disabled={disabled} onClick={() => setRemoving(true)}><DeleteOutlineRounded /></IconButton></span></Tooltip> : null}
        </Stack>
      </Stack>
      {error ? <Alert severity="error">{error}</Alert> : null}
      {catalogue.actions.length === 0 ? <Alert severity="info">No action definitions in this draft.</Alert> : !action ?
        <Alert severity="error">The selected action is not a JSON object. Repair it in Advanced catalogue JSON.</Alert> : <>
        <Box sx={fields}>
          <TextField size="small" label="Name" required value={name} error={!name.trim()} InputProps={{ readOnly: true }}
            helperText={ACTION_RENAME_MESSAGE} />
          <TextField size="small" label="Display name" value={text(action, 'displayName')} disabled={disabled}
            placeholder="e.g. Look up an order" helperText="Optional customer-facing label."
            onChange={event => patch({ displayName: event.target.value })} />
          <TextField size="small" label="Description" multiline minRows={2} value={text(action, 'description')} disabled={disabled}
            sx={{ gridColumn: '1 / -1' }} placeholder="e.g. Look up the current status of an order by its reference."
            onChange={event => patch({ description: event.target.value })} />
          <TextField size="small" label="Category" value={text(action, 'category')} disabled={disabled}
            placeholder="e.g. orders" onChange={event => patch({ category: event.target.value })} />
        </Box>
        {mcp ? <McpActionDestination action={action} destination={mcp} capabilitiesHref={brain?.href('capabilities')} /> : onEditRoute && name ? <Button startIcon={<RouteRounded />} sx={{ alignSelf: 'flex-start' }}
          onClick={() => onEditRoute(name)}>HTTP route for {name}</Button> : null}

        <ConfigurationDetails label="Result presentation">
          <TextField select fullWidth size="small" label="Result presentation hint" value={text(action, 'resultPresentationHint')} disabled={disabled}
            helperText="Advisory presentation hint; does not change execution or access."
            onChange={event => patch({ resultPresentationHint: event.target.value || undefined })}>
            <MenuItem value="">Automatic ({action.accessMode === 'WRITE_ONLY' ? 'STATUS' : 'DEFAULT'})</MenuItem>
            {text(action, 'resultPresentationHint') && !ACTION_RESULT_HINTS.some(hint => hint === action.resultPresentationHint) ?
              <MenuItem value={text(action, 'resultPresentationHint')}>{text(action, 'resultPresentationHint')}</MenuItem> : null}
            {ACTION_RESULT_HINTS.map(hint => <MenuItem key={hint} value={hint}>{hint}</MenuItem>)}
          </TextField>
        </ConfigurationDetails>

        <ConfigurationDetails label="Access & confirmation">
          <Stack spacing={2}>
            <TextField select required size="small" label="Access mode" value={text(action, 'accessMode')} disabled={disabled}
              error={!ACTION_ACCESS_MODES.some(mode => mode === action.accessMode)} helperText="READ has no side effects. READ_WRITE and WRITE_ONLY change state."
              onChange={event => patch({ accessMode: event.target.value })}>
              {!ACTION_ACCESS_MODES.some(mode => mode === action.accessMode) ? <MenuItem value={text(action, 'accessMode')}>{text(action, 'accessMode') || 'Select access mode'}</MenuItem> : null}
              {ACTION_ACCESS_MODES.map(mode => <MenuItem key={mode} value={mode}>{mode}</MenuItem>)}
            </TextField>
            <FormControlLabel control={<Checkbox checked={action.anonymousAllowed === true} disabled={disabled}
              onChange={event => patch({ anonymousAllowed: event.target.checked })} />} label="Allow anonymous access" />
            <FormControlLabel control={<Checkbox checked={action.requiresConfirmation === true} disabled={disabled}
              onChange={event => patch({ requiresConfirmation: event.target.checked })} />} label="Require confirmation" />
            {action.requiresConfirmation === true || text(action, 'confirmationMessage') ? <TextField size="small" multiline minRows={2}
              label="Confirmation message" value={text(action, 'confirmationMessage')} disabled={disabled}
              placeholder="e.g. Send this callback request?" helperText="Optional. Existing messages are retained when confirmation is turned off."
              onChange={event => patch({ confirmationMessage: event.target.value })} /> : null}
          </Stack>
        </ConfigurationDetails>

        <ConfigurationDetails label={`Input parameters (${params.values.length})`}>
          <Stack spacing={2}>
            {mcp ? <Typography variant="body2" color="text.secondary">Parameter names, types and requirements come from the read-only native schema. Local descriptions, missing-input collection, typed defaults and trusted resolvers remain configurable.</Typography> : null}
            {params.error ? <Alert severity="error">{params.error}</Alert> : <>
              {params.values.length === 0 ? <Alert severity="info">No input parameters.</Alert> : null}
              {params.values.map((param, paramIndex) => <Box key={paramIndex} sx={{ borderBottom: 1, borderColor: 'divider', pb: 2 }}>
                <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: 2 }}>
                  <Typography variant="subtitle2">Parameter {paramIndex + 1}</Typography>
                  {!mcp ? <Tooltip title={`Remove parameter ${paramIndex + 1}`}><span><IconButton size="small" aria-label={`Remove parameter ${paramIndex + 1}`}
                    disabled={disabled} onClick={() => mutate(() => removeActionParameter(editorValue, index, paramIndex))}><DeleteOutlineRounded fontSize="small" /></IconButton></span></Tooltip>
                    : null}
                </Stack>
                {!isJsonObject(param) ? <Alert severity="error">This parameter is not a JSON object. Repair it in Advanced action JSON.</Alert> : <Stack spacing={1.5}>
                  <Box sx={fields}>
                    <TextField size="small" label="Parameter name" required disabled={disabled} InputProps={{ readOnly: Boolean(mcp) }} value={text(param, 'name')}
                      error={!text(param, 'name').trim() || params.values.some((other, otherIndex) => otherIndex !== paramIndex && isJsonObject(other) && other.name === param.name)}
                      helperText={mcp ? 'Exact imported name. Refresh the source tools to change its schema.' : 'Required; unique in this action. Example: orderReference.'}
                      onChange={event => patchParam(paramIndex, { name: event.target.value })} />
                    <TextField select size="small" label="Type" required disabled={disabled || Boolean(mcp)} value={text(param, 'type')}
                      error={!ACTION_PARAM_TYPES.some(type => type === param.type)}
                      onChange={event => patchParam(paramIndex, { type: event.target.value })}>
                      {!ACTION_PARAM_TYPES.some(type => type === param.type) ? <MenuItem value={text(param, 'type')}>{text(param, 'type') || 'Select type'}</MenuItem> : null}
                      {ACTION_PARAM_TYPES.map(type => <MenuItem key={type} value={type}>{type}</MenuItem>)}
                    </TextField>
                    <TextField size="small" multiline minRows={2} label="Parameter description" disabled={disabled} value={text(param, 'description')}
                      sx={{ gridColumn: '1 / -1' }} placeholder="e.g. The order reference supplied by the customer."
                      onChange={event => patchParam(paramIndex, { description: event.target.value })} />
                    <TextField select size="small" label="Source" disabled={disabled} value={text(param, 'visibility') || 'USER'}
                      helperText="Connector field: visibility. Omitted means USER."
                      onChange={event => patchParam(paramIndex, { visibility: event.target.value })}>
                      {text(param, 'visibility') && !ACTION_PARAM_SOURCES.some(source => source === param.visibility) ? <MenuItem value={text(param, 'visibility')}>{text(param, 'visibility')}</MenuItem> : null}
                      {ACTION_PARAM_SOURCES.map(source => <MenuItem key={source} value={source}>{source}</MenuItem>)}
                    </TextField>
                  </Box>
                  <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
                    <FormControlLabel control={<Checkbox checked={param.required === true} disabled={disabled || Boolean(mcp)}
                      onChange={event => patchParam(paramIndex, { required: event.target.checked })} />} label="Required" />
                    <FormControlLabel control={<Checkbox checked={param.askUser !== false} disabled={disabled}
                      onChange={event => patchParam(paramIndex, { askUser: event.target.checked })} />} label="Ask user for missing value" />
                  </Stack>
                  {param.visibility === 'INTERNAL' || param.askUser === false ? <Alert severity="info">Missing values need trusted runtime context when Ask user is off. Source does not change resolver or evidence rules.</Alert> : null}
                  <ConfigurationDetails label={mcp ? 'Local default & sensitivity' : 'Defaults & validation'}>
                    <Stack spacing={2}>
                      {parameterJsonControl(param, paramIndex, 'defaultValue')}
                      {!mcp ? parameterJsonControl(param, paramIndex, 'allowedValues') : null}
                      {!mcp ? <Box sx={fields}>
                        <ParameterIntegerField label="Minimum (min)" value={param.min} disabled={disabled} onChange={value => patchParam(paramIndex, { min: value })} />
                        <ParameterIntegerField label="Maximum (max)" value={param.max} disabled={disabled} onChange={value => patchParam(paramIndex, { max: value })} />
                      </Box> : null}
                      <FormControlLabel control={<Checkbox checked={param.sensitive === true} disabled={disabled}
                        onChange={event => patchParam(paramIndex, { sensitive: event.target.checked })} />} label="Sensitive value" />
                    </Stack>
                  </ConfigurationDetails>
                  <ConfigurationDetails label={mcp ? 'Trusted input' : 'Trusted input & nested schema'}>
                    <Stack spacing={2}>
                      {parameterJsonControl(param, paramIndex, 'resolveFrom')}
                      {!mcp && (param.type === 'ARRAY' || param.items !== undefined) ? parameterJsonControl(param, paramIndex, 'items') : null}
                      {!mcp && (param.type === 'OBJECT' || param.properties !== undefined) ? parameterJsonControl(param, paramIndex, 'properties') : null}
                      {!mcp && (param.type === 'OBJECT' || param.requiredProperties !== undefined) ? parameterJsonControl(param, paramIndex, 'requiredProperties') : null}
                    </Stack>
                  </ConfigurationDetails>
                </Stack>}
              </Box>)}
              {!mcp ? <Button startIcon={<AddRounded />} variant="outlined" disabled={disabled} sx={{ alignSelf: 'flex-start' }}
                onClick={() => mutate(() => appendActionParameter(editorValue, index))}>Add parameter</Button> : null}
            </>}
          </Stack>
        </ConfigurationDetails>

        <ConfigurationDetails label="Grounding & read resolution">
          <Stack spacing={2}>
            <FormControlLabel control={<Checkbox checked={grounding} disabled={disabled}
              onChange={event => patch({ groundingEligible: event.target.checked })} />} label="Grounding eligible" />
            <Autocomplete multiple freeSolo options={[]} disabled={disabled || invalidSpaces}
              value={Array.isArray(action.groundingVectorSpaces) ? action.groundingVectorSpaces.filter((space): space is string => typeof space === 'string') : []}
              onChange={(_, value) => patch({ groundingVectorSpaces: value })}
              renderInput={input => <TextField {...input} size="small" label="Grounding vector spaces (optional)"
                error={invalidSpaces} placeholder="e.g. product-catalog" helperText={invalidSpaces
                  ? 'groundingVectorSpaces must be a string array. Repair it in Advanced action JSON.'
                  : 'READ actions do not require a vector space unless their grounding contract needs one.'} />} />
            <FormControlLabel control={<Checkbox checked={action.readActionResolutionEligible === true} disabled={disabled}
              onChange={event => patch({ readActionResolutionEligible: event.target.checked })} />} label="Read-action resolution eligible" />
            {action.accessMode !== 'READ' && action.readActionResolutionEligible === true ? <Alert severity="warning">Read-action resolution requires READ access. This value has not been changed automatically.</Alert> : null}
            {Array.isArray(action.groundingVectorSpaces) && action.groundingVectorSpaces.length > 0 && (action.accessMode !== 'READ' || !grounding) ?
              <Alert severity="warning">Grounding vector spaces require a grounding-eligible READ action. Existing spaces are retained.</Alert> : null}
          </Stack>
        </ConfigurationDetails>
        <Divider />
        <Button startIcon={<CodeRounded />} variant="text" disabled={disabled} sx={{ alignSelf: 'flex-start' }} onClick={() => {
          setJsonError(null)
          setJsonEdit({ source: editorValue, index, value: stringifyExactJson(mcp ? mcpLocalSettings(action) : action, 2) })
        }}>Advanced action JSON</Button>
      </>}
    </>}

    <Dialog open={adding} onClose={() => setAdding(false)} fullWidth maxWidth="sm" aria-labelledby="add-action-title">
      <DialogTitle id="add-action-title">Add action</DialogTitle>
      <DialogContent><TextField autoFocus fullWidth required size="small" label="New action name" sx={{ mt: 1 }}
        value={newName} disabled={disabled} error={newName.length > 0 && Boolean(addError)}
        helperText={newName.length > 0 && addError ? addError : 'Example: lookup_order. This name is used by routes and cannot be renamed here.'}
        onChange={event => setNewName(event.target.value)} /></DialogContent>
      <DialogActions><Button onClick={() => setAdding(false)}>Cancel</Button><Button startIcon={<AddRounded />} variant="contained" disabled={disabled || Boolean(addError) || Boolean(catalogue.error)} onClick={() => {
        mutate(() => appendActionDefinition(editorValue, newName))
        setSelection({ index: catalogue.actions.length, name: newName.trim() })
        setAdding(false)
      }}>Add action</Button></DialogActions>
    </Dialog>

    <Dialog open={removing} onClose={() => setRemoving(false)} fullWidth maxWidth="sm" aria-labelledby="remove-action-title">
      <DialogTitle id="remove-action-title">Remove action {name || index + 1}?</DialogTitle>
      <DialogContent>{removalBlock ? <Alert severity="warning">{removalBlock}</Alert> :
        <Typography variant="body2">The selected definition and its parameters will be removed from this draft. Other actions and catalogue settings are retained.</Typography>}</DialogContent>
      <DialogActions><Button onClick={() => setRemoving(false)}>Cancel</Button><Button color="error" startIcon={<DeleteOutlineRounded />} disabled={disabled || Boolean(removalBlock)} onClick={() => {
        mutate(() => removeActionDefinition(editorValue, index, routedActionNames))
        setSelection({ index: Math.max(0, index - 1), name: '' })
        setRemoving(false)
      }}>Remove action</Button></DialogActions>
    </Dialog>

    <Dialog open={jsonEdit !== null} onClose={() => setJsonEdit(null)} fullWidth maxWidth="md" aria-labelledby="action-json-title">
      <DialogTitle id="action-json-title">{jsonEdit?.field ? parameterJsonLabels[jsonEdit.field] : mcp ? 'Advanced local action JSON' : 'Advanced action JSON'}</DialogTitle>
      <DialogContent><Stack spacing={2} sx={{ pt: 1 }}>
        <TextField fullWidth multiline minRows={jsonEdit?.field ? 6 : 16} maxRows={24} label={jsonEdit?.field ? `${parameterJsonLabels[jsonEdit.field]} JSON` : mcp ? 'Local action settings JSON' : 'Action definition JSON'} value={jsonEdit?.value ?? ''}
          disabled={disabled || jsonStale} onChange={event => { setJsonEdit(current => current ? { ...current, value: event.target.value } : null); setJsonError(null) }}
          helperText={jsonEdit?.field ? parameterJsonHelp[jsonEdit.field] : mcp ? 'Local settings only. Typed defaultValue and resolveFrom are configurable; imported parameter contracts, native schemas and connection settings are read-only.' : 'Parameters use defaultValue, allowedValues and resolveFrom. Nested schemas use items, properties and requiredProperties.'}
          InputProps={{ sx: { fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace', fontSize: 13 } }} />
        {jsonError ? <Alert severity="error">{jsonError}</Alert> : null}
        {jsonStale ? <Alert severity="warning">The current buffer changed. Close and reopen Advanced action JSON before editing.</Alert> : null}
      </Stack></DialogContent>
      <DialogActions><Button onClick={() => setJsonEdit(null)}>Cancel</Button><Button startIcon={<CodeRounded />} variant="contained" disabled={disabled || jsonStale} onClick={() => {
        if (!jsonEdit || disabled || jsonStale) return
        try {
          onChange(jsonEdit.field && jsonEdit.paramIndex !== undefined
            ? patchActionParameter(editorValue, jsonEdit.index, jsonEdit.paramIndex, { [jsonEdit.field]: parseActionParameterJson(jsonEdit.field, jsonEdit.value) })
            : mcpActionDestination(isJsonObject(catalogue.actions[jsonEdit.index]) ? catalogue.actions[jsonEdit.index] as JsonObject : null)
              ? replaceMcpLocalSettings(editorValue, jsonEdit.index, parseExactObject(jsonEdit.value, 'Local action settings'))
              : replaceActionDefinition(editorValue, jsonEdit.index, parseExactObject(jsonEdit.value, 'Action definition')))
          setJsonEdit(null)
          setError(null)
        } catch (error) { setJsonError(error instanceof Error ? error.message : 'Invalid action JSON.') }
      }}>Apply to buffer</Button></DialogActions>
    </Dialog>
  </Stack>
}
