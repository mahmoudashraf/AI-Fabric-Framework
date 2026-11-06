import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Alert, Button, CircularProgress, MenuItem, TextField, ToggleButton, ToggleButtonGroup } from '@mui/material'
import ArrowBackRounded from '@mui/icons-material/ArrowBackRounded'
import ArrowForwardRounded from '@mui/icons-material/ArrowForwardRounded'
import CheckRounded from '@mui/icons-material/CheckRounded'
import LanguageRounded from '@mui/icons-material/LanguageRounded'
import WebOutlined from '@mui/icons-material/WebOutlined'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { deploymentTerm } from '../../deploymentTerminology'
import { Link, useNavigate } from 'react-router-dom'
import { enrollPublicCustomerTemplates, type AiStateStorageMode, type CustomerOfferSummary } from '../../api/platformApi'
import { createExperienceKit, experienceKitKeys, fetchExperienceKitCatalogue, type CreateExperienceKitRequest, type ExperienceKitCatalogueEntry, type ExperienceKitSummary } from '../../api/experienceKitsApi'
import { aiStateStorageLabels, aiStateStorageModes } from '../customer/customerAiStateStorage'
import { KitExperienceFields } from './KitExperienceFields'
import { KitMark, KitProductLine } from './KitIdentity'
import { KitWorkspacePreview } from './KitWorkspacePreview'
import { kitAppAddressPreview, kitDeliveryMode, kitExperienceErrors, kitStagingTargets, type KitExperienceForm } from './kitModels'

export type ExperienceKitSetupTransport = {
  prepare: () => Promise<unknown>
  fetchCatalogue: (signal?: AbortSignal) => Promise<ExperienceKitCatalogueEntry[]>
  create: (body: CreateExperienceKitRequest) => Promise<ExperienceKitSummary>
  queryScope: readonly string[]
}

type SetupProps = {
  customerId: string; actorId: string; code: string; transport?: ExperienceKitSetupTransport; basePath?: string; preparationRevision?: number
}

export function ExperienceKitSetup(props: SetupProps) {
  return <PreparedKitSetup key={JSON.stringify([props.customerId, props.actorId, props.code, props.basePath, props.transport?.queryScope])} {...props} />
}

function PreparedKitSetup({ customerId, actorId, code, transport, basePath = '/kits', preparationRevision = 0 }: SetupProps) {
  const [prepared, setPrepared] = useState(false)
  const [prepareError, setPrepareError] = useState<Error | null>(null)
  const [attempt, setAttempt] = useState(0)
  const enrollment = useRef<Promise<unknown> | null>(null)
  const accessRevision = useRef(preparationRevision)
  const queryClient = useQueryClient()
  useEffect(() => {
    let current = true
    if (accessRevision.current !== preparationRevision) {
      accessRevision.current = preparationRevision
      enrollment.current = null
      setPrepareError(null)
    }
    if (transport) {
      if (!enrollment.current) enrollment.current = transport.prepare()
    } else {
      if (!enrollment.current) enrollment.current = enrollPublicCustomerTemplates()
    }
    enrollment.current.then(async () => {
      if (!current) return
      await queryClient.invalidateQueries({ queryKey: transport ? [...transport.queryScope, 'catalogue'] : experienceKitKeys.catalogue(customerId) })
      if (current) setPrepared(true)
    }).catch(error => { if (current) setPrepareError(error instanceof Error ? error : new Error('Template access could not be prepared.')) })
    return () => { current = false }
  }, [customerId, code, attempt, queryClient, transport, preparationRevision])
  const catalogue = useQuery({ queryKey: transport ? [...transport.queryScope, 'catalogue'] : experienceKitKeys.catalogue(customerId),
    queryFn: ({ signal }) => transport ? transport.fetchCatalogue(signal) : fetchExperienceKitCatalogue(signal), retry: false, enabled: prepared })
  const entry = catalogue.data?.find(item => item.definition.code === code)
  if (prepareError) return <Alert severity="error" action={<Button onClick={() => { enrollment.current = null; setPrepareError(null); setAttempt(value => value + 1) }}>Retry</Button>}>{prepareError.message}<Button component={Link} to={transport ? basePath : '/support'}>{transport ? 'Back to customer kits' : 'Request integration help'}</Button></Alert>
  if (!prepared) return <div className="ek-query-state" role="status"><CircularProgress size={24} />Preparing your selected kit...</div>
  if (catalogue.isPending) return <div className="ek-query-state" role="status"><CircularProgress size={24} />Loading kit setup...</div>
  if (catalogue.isError) return <Alert severity="error" action={<Button onClick={() => void catalogue.refetch()}>Retry</Button>}>{catalogue.error instanceof Error ? catalogue.error.message : 'Kit setup could not be loaded.'}</Alert>
  if (!entry) return <Alert severity="info" action={<Button component={Link} to={transport ? basePath : '/support'}>{transport ? 'Back to customer kits' : 'Get setup help'}</Button>}>This experience is not available for this account yet. Nothing has been created.</Alert>
  return <KitSetupForm key={`${code}:${entry.definition.version}`} customerId={customerId} actorId={actorId} entry={entry} transport={transport} basePath={basePath} />
}

function initialOffer(offers: CustomerOfferSummary[]) {
  return offers.length === 1 ? offers[0] : undefined
}

function storageChoices(offer?: CustomerOfferSummary) {
  try { return { modes: offer ? aiStateStorageModes(offer) : null, error: null } }
  catch (error) { return { modes: null, error: error instanceof Error ? error.message : 'AI-state storage choices could not be loaded.' } }
}

function KitSetupForm({ customerId, actorId, entry, transport, basePath }: {
  customerId: string; actorId: string; entry: ExperienceKitCatalogueEntry; transport?: ExperienceKitSetupTransport; basePath: string
}) {
  const { definition, offers } = entry
  const supportExperience = definition.code === 'ai-support-experience'
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [step, setStep] = useState(0)
  const [offerId, setOfferId] = useState(initialOffer(offers)?.id ?? '')
  const offer = offers.find(item => item.id === offerId)
  const [targetProfileId, setTargetProfileId] = useState(() => {
    const targets = kitStagingTargets(initialOffer(offers))
    return targets.length === 1 ? targets[0].id : ''
  })
  const [workspaceProfileId, setWorkspaceProfileId] = useState(() => {
    const profiles = initialOffer(offers)?.workspaceProfiles ?? []
    return profiles.length === 1 ? profiles[0].id : ''
  })
  const [storageMode, setStorageMode] = useState<AiStateStorageMode | ''>(() => {
    const modes = storageChoices(initialOffer(offers)).modes
    return modes?.length === 1 ? modes[0] : ''
  })
  const [form, setForm] = useState<KitExperienceForm>({ name: definition.name, workspaceName: definition.defaultWorkspaceName,
    brandColor: definition.code === 'dealership-ai-experience' ? '#409aff' : '#28bfa6',
    embedded: false, standalone: true, appSlug: '', websiteOrigin: '', humanSupportEnabled: false, ticketingEnabled: false, ticketProvider: null })
  const [previewLayout, setPreviewLayout] = useState<'standalone' | 'embedded'>('standalone')
  const [error, setError] = useState<string | null>(null)
  const operation = useRef<{ signature: string; key: string } | null>(null)
  const operationStorageKey = `${transport ? 'loomai:staff-kit-create' : 'loomai:kit-create'}:${customerId}:${actorId}:${definition.code}`
  const mounted = useRef(true)
  useEffect(() => { mounted.current = true; return () => { mounted.current = false } }, [])
  const create = useMutation({ mutationFn: transport?.create ?? createExperienceKit })
  const { modes, error: storageError } = storageChoices(offer)
  const targets = kitStagingTargets(offer)
  const workspaceProfile = offer?.workspaceProfiles.find(profile => profile.id === workspaceProfileId)
  const selectionErrors = [
    ...(!offer ? ['Choose an available starting experience.'] : []),
    ...(offer && offer.entitlement.customerId !== customerId ? ['The selected template belongs to a different customer.'] : []),
    ...(offer && offer.entitlement.status !== 'ACTIVE' ? ['The selected template is not active for this customer.'] : []),
    ...(!targets.some(target => target.id === targetProfileId) ? ['Choose a test environment.'] : []),
    ...(!workspaceProfile ? ['Choose a workspace style.'] : []),
    ...(storageError ? [storageError] : modes && !modes.includes(storageMode as AiStateStorageMode) ? ['Choose where conversations will be stored.'] : []),
    ...(!form.name.trim() ? ['Enter a name for your kit.'] : []),
  ]
  const setOffer = (id: string) => {
    const next = offers.find(item => item.id === id)
    setOfferId(id)
    const nextTargets = kitStagingTargets(next)
    setTargetProfileId(nextTargets.length === 1 ? nextTargets[0].id : '')
    setWorkspaceProfileId(next?.workspaceProfiles.length === 1 ? next.workspaceProfiles[0].id : '')
    const nextModes = storageChoices(next).modes
    setStorageMode(nextModes?.length === 1 ? nextModes[0] : '')
    setError(null)
  }
  const nextStep = () => {
    const errors = step === 0 ? selectionErrors : kitExperienceErrors(form, supportExperience ? entry.ticketProviders : undefined)
    if (errors.length) { setError(errors[0]); return }
    setError(null); setStep(value => Math.min(2, value + 1))
  }
  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (step < 2) { nextStep(); return }
    const errors = [...selectionErrors, ...kitExperienceErrors(form, supportExperience ? entry.ticketProviders : undefined)]
    if (errors.length) { setError(errors[0]); return }
    const payload: Omit<CreateExperienceKitRequest, 'operationKey'> = {
      kitCode: definition.code, name: form.name.trim(), offerId, targetProfileId, workspaceProfileId,
      ...(modes && storageMode ? { aiStateStorageMode: storageMode } : {}),
      workspaceName: form.workspaceName.trim(), brandColor: form.brandColor, deliveryMode: kitDeliveryMode(form)!,
      ...(form.standalone ? { appSlug: form.appSlug } : {}),
      ...(form.embedded ? { websiteOrigin: new URL(form.websiteOrigin.trim()).origin } : {}),
      humanSupportEnabled: form.humanSupportEnabled, ticketingEnabled: form.ticketingEnabled, ticketProvider: form.ticketProvider,
    }
    const signature = JSON.stringify([customerId, payload])
    if (!operation.current) {
      try {
        const stored = JSON.parse(sessionStorage.getItem(operationStorageKey) ?? 'null')
        if (stored?.signature === signature && typeof stored.key === 'string' && stored.key.length <= 128) operation.current = stored
      } catch { /* Only the request key is retained; blocked storage does not block setup. */ }
    }
    if (operation.current?.signature !== signature) operation.current = { signature, key: crypto.randomUUID() }
    try { sessionStorage.setItem(operationStorageKey, JSON.stringify(operation.current)) } catch { /* The in-memory key still protects retries in this form. */ }
    setError(null)
    try {
      const kit = await create.mutateAsync({ ...payload, operationKey: operation.current.key })
      if (!mounted.current) return
      try { sessionStorage.removeItem(operationStorageKey) } catch { /* No kit data is persisted in browser storage. */ }
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: transport?.queryScope ?? experienceKitKeys.customer(customerId) }),
        queryClient.invalidateQueries({ queryKey: ['customer-deployment-portfolio', customerId] }),
        queryClient.invalidateQueries({ queryKey: ['ai-workspace-installations', customerId] }),
      ])
      if (mounted.current) navigate(`${basePath}/${encodeURIComponent(kit.id)}`)
    } catch (failure) {
      if (mounted.current) setError(failure instanceof Error ? failure.message : 'The kit could not be created. Retry to check the same operation.')
    }
  }
  const dealership = definition.code === 'dealership-ai-experience'
  return <form className="ek-setup" noValidate onSubmit={event => void submit(event)}>
    <Button component={Link} to={basePath} startIcon={<ArrowBackRounded />} className="ek-back">{transport ? 'Back to customer kits' : 'Back to experiences'}</Button>
    <div className="ek-detail-title"><KitMark product={dealership ? 'dealership' : 'engage'} large /><div><h1>{definition.name}</h1><p>{definition.summary}</p><KitProductLine dealership={dealership} support={form.humanSupportEnabled} /></div><span className="ek-badge">Not live yet</span></div>
    <ol className="ek-setup-steps" aria-label="Kit setup progress">{['Choose your starting point', 'Make it yours', 'Review your setup'].map((label, index) => <li key={label} data-step-state={index === step ? 'current' : index < step ? 'done' : 'next'}>
      <button type="button" disabled={index > step || create.isPending} onClick={() => { setStep(index); setError(null) }} aria-current={index === step ? 'step' : undefined}>
        <span>{index < step ? <CheckRounded /> : index + 1}</span>{label}</button></li>)}</ol>
    {error && <Alert severity="error" className="ek-form-error">{error}</Alert>}
    <div className="ek-setup-grid">
      <section className="ek-setup-preview" aria-labelledby="ek-preview-title"><div className="ek-preview-section-heading"><h2 id="ek-preview-title">Customer experience preview</h2>
        <ToggleButtonGroup size="small" exclusive value={previewLayout} aria-label="Preview delivery channel"
          onChange={(_, value: 'standalone' | 'embedded' | null) => { if (value) setPreviewLayout(value) }}>
          <ToggleButton value="embedded"><LanguageRounded />Website widget</ToggleButton><ToggleButton value="standalone"><WebOutlined />Standalone app</ToggleButton>
        </ToggleButtonGroup></div>
        <KitWorkspacePreview packCode={definition.experiencePackCode} workspaceName={form.workspaceName} brandColor={form.brandColor}
          appUrl={form.standalone ? kitAppAddressPreview(form.appSlug) : null}
          configuration={{ ...(workspaceProfile?.interaction.configuration as Record<string, unknown> | undefined),
            theme: { ...(workspaceProfile?.theme ?? {}), primaryColor: form.brandColor } }} humanSupportEnabled={form.humanSupportEnabled}
          ticketingEnabled={form.ticketingEnabled} layout={previewLayout} />
        <div className="ek-connected-flow"><div><KitMark product="platform" /><span><strong>Your AI assistant</strong><small>Your information and connected systems</small></span></div><ArrowForwardRounded />
          <div><KitMark product="engage" /><span><strong>Your customer workspace</strong><small>Your brand, on your website or its own address</small></span></div>
          {form.humanSupportEnabled && <><ArrowForwardRounded /><div><KitMark product="desk" /><span><strong>Desk team</strong><small>Your human agents (optional)</small></span></div></>}
        </div>
      </section>
      <aside className="ek-setup-controls" aria-label="Kit configuration">
        {step === 0 ? <>
          <h2>Choose your starting point</h2>
          <TextField label="Kit name" required size="small" fullWidth value={form.name} disabled={create.isPending} inputProps={{ maxLength: 120 }} onChange={event => setForm({ ...form, name: event.target.value })} />
          <TextField label="Starting experience" size="small" select fullWidth required value={offerId} onChange={event => setOffer(event.target.value)}>
            <MenuItem value="" disabled>Choose a starting experience</MenuItem>{offers.map(item => <MenuItem key={item.id} value={item.id}>{item.name}</MenuItem>)}
          </TextField>
          {!offers.length && <Alert severity="info">No active template is available. Return to My kits and refresh available templates.</Alert>}
          {offer?.blockers.map((blocker, index) => <Alert severity="warning" key={`offer:${index}`}>{blocker}</Alert>)}
          {targets.find(target => target.id === targetProfileId)?.blockers.map((blocker, index) => <Alert severity="warning" key={`target:${index}`}>{blocker}</Alert>)}
          {targets.length === 1 ? <div className="ek-target-fact"><small>Start safely</small><strong>Test before going live</strong></div>
            : <TextField label="Test environment" size="small" select fullWidth value={targetProfileId} onChange={event => setTargetProfileId(event.target.value)}>
              <MenuItem value="" disabled>Choose a test environment</MenuItem>{targets.map((target, index) => <MenuItem key={target.id} value={target.id}>Test environment{targets.length > 1 ? ` ${index + 1} (${target.id})` : ''}</MenuItem>)}
            </TextField>}
          <TextField label="Workspace style" size="small" select required fullWidth value={workspaceProfileId} onChange={event => setWorkspaceProfileId(event.target.value)}>
            <MenuItem value="" disabled>Choose a configuration</MenuItem>{offer?.workspaceProfiles.map(profile => <MenuItem key={profile.id} value={profile.id}>{profile.label}</MenuItem>)}
          </TextField>
          {modes && <TextField label="Where conversations are stored" size="small" select required fullWidth value={storageMode} onChange={event => setStorageMode(event.target.value as AiStateStorageMode)}
            helperText="Your team can connect this before going live. Nothing is provisioned at this step.">
            <MenuItem value="" disabled>Choose storage</MenuItem>{modes.map(mode => <MenuItem key={mode} value={mode}>{aiStateStorageLabels[mode]}</MenuItem>)}
          </TextField>}
          {storageError && <Alert severity="error">{storageError}</Alert>}
          {offer && <div className="ek-configured-parts"><h3>Your information, your business</h3><p>Add your approved documents and connect the systems your customers need before going live.</p>
            <details className="ek-advanced"><summary>Connection details for your team</summary>{offer.bindingSlots.length ? <ul>{offer.bindingSlots.map(slot => <li key={slot.id}>{slot.id}<small>{slot.kind.replace(/_/g, ' ').toLowerCase()}</small></li>)}</ul>
              : <p>No external connection slots are declared by this template.</p>}<p>Technical setup uses the {deploymentTerm.lowercase} editor after your workspace is created.</p></details></div>}
        </> : step === 1 ? <><h2>Your experience</h2><KitExperienceFields value={form} onChange={setForm} disabled={create.isPending}
          supportExperience={supportExperience} ticketProviders={entry.ticketProviders} /></>
          : <><h2>Review your kit</h2><dl className="ek-review-facts">
            <div><dt>Kit</dt><dd>{form.name}</dd></div><div><dt>Starting experience</dt><dd>{offer?.name}</dd></div>
            <div><dt>First step</dt><dd>Test before going live</dd></div><div><dt>Workspace</dt><dd>{form.workspaceName}</dd></div>
            <div><dt>Where customers find you</dt><dd>{form.standalone && form.embedded ? 'Your website + standalone app' : form.embedded ? 'Your website' : 'Standalone app'}</dd></div>
            {form.standalone && <div><dt>App link</dt><dd>{kitAppAddressPreview(form.appSlug)}</dd></div>}
            {form.embedded && <div><dt>Website address</dt><dd>{form.websiteOrigin}</dd></div>}
            {supportExperience && <><div><dt>Request tracking</dt><dd>{form.ticketingEnabled ? 'Setup requested' : 'Not requested'}</dd></div>
              {form.ticketProvider && <div><dt>Ticket provider</dt><dd>{entry.ticketProviders.find(provider => provider.code === form.ticketProvider)?.name ?? 'Unavailable provider'}</dd></div>}</>}
            <div><dt>Human support</dt><dd>{form.humanSupportEnabled ? 'Add your team after setup' : 'Optional, not requested'}</dd></div>
          </dl><Alert severity="info">Creates your workspace setup only. Your AI assistant is not live yet, and no paid services are started at this step.</Alert>
            <p className="ek-muted">Next: connect your information, test the experience and choose when to go live. You can get help from our integration team.</p>
          </>}
        <div className="ek-setup-actions">{step > 0 && <Button type="button" onClick={() => { setStep(value => value - 1); setError(null) }} disabled={create.isPending} startIcon={<ArrowBackRounded />}>Back</Button>}
          <Button type="submit" variant="contained" className="ek-primary-button" disabled={create.isPending || (step === 0 && !offers.length)} endIcon={create.isPending ? <CircularProgress size={16} color="inherit" /> : <ArrowForwardRounded />}>
            {create.isPending ? 'Creating workspace...' : step === 2 ? transport ? 'Create customer workspace' : 'Create my workspace' : 'Continue setup'}</Button></div>
        {!transport && <Link className="ek-help-link" to="/support">Get setup help <ArrowForwardRounded /></Link>}
      </aside>
    </div>
  </form>
}
