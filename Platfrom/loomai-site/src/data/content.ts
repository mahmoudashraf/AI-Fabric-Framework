import type {
  ContentRelation,
  Experiment,
  NavigationItem,
  Product,
  ResearchArtifact,
} from './types'

export const site = {
  name: 'Loom AI Labs',
  description:
    'Open-source products, live experiments and applied research for dependable AI-enabled applications.',
  canonicalOrigin: 'https://loomai.pro',
  maintainer: 'Mahmoud Ashraf Algammal',
  email: 'hello@loomai.pro',
} as const

export const navigation: NavigationItem[] = [
  { label: 'Products', href: '/products', activePatterns: ['/products'] },
  { label: 'Live Experiments', href: '/experiments', activePatterns: ['/experiments'] },
  { label: 'Applied Research', href: '/research', activePatterns: ['/research'] },
  { label: 'About', href: '/about', activePatterns: ['/about'] },
]

export const products: Product[] = [
  {
    id: 'product:ai-fabric-framework',
    slug: 'ai-fabric-framework',
    name: 'AI Fabric Framework',
    shortName: 'Framework',
    layerLabel: 'Application enablement',
    statusLabel: 'Early release',
    version: '0.4.0',
    licence: 'Apache 2.0',
    summary:
      'A Spring-native foundation for grounding, retrieval, governed actions and application-owned AI orchestration.',
    description:
      'AI Fabric Framework gives Java teams explicit contracts for connecting models to live application data and operations. The host application retains identity, authorization, policy and business execution.',
    problem:
      'Useful AI features need more than a model call. They need application context, current data, observable evidence and action boundaries that survive real software lifecycle changes.',
    value:
      'AI Fabric makes those boundaries explicit and reusable while staying close to Spring Boot and Spring AI. Teams can adopt the capabilities they need without moving their domain into a second application platform.',
    capabilities: [
      {
        name: 'Application context',
        description: 'Carry trusted tenant, identity and request context through orchestration.',
      },
      {
        name: 'Grounded retrieval',
        description: 'Index, route and return evidence from application-managed knowledge.',
      },
      {
        name: 'Governed actions',
        description: 'Describe, validate, confirm and observe application-owned operations.',
      },
      {
        name: 'Data lifecycle',
        description: 'Synchronize entities and keep index state aligned with source records.',
      },
      {
        name: 'Operational evidence',
        description: 'Expose traces, decisions and result contracts for inspection and testing.',
      },
    ],
    owns: [
      'Orchestration contracts and pipeline behavior',
      'Retrieval, evidence and structured result contracts',
      'Action proposals, confirmation state and execution hand-off',
      'Provider-neutral configuration and extension points',
    ],
    hostOwns: [
      'User identity, sessions and tenant authorization',
      'Business rules, resource ownership and source-of-truth data',
      'Action implementation and final permission checks',
      'Product UI, customer promises and operational policy',
    ],
    flow: ['Application context', 'AI Fabric orchestration', 'Data and tools', 'Application response'],
    quickStart: {
      language: 'xml',
      code: `<dependencyManagement>
  <dependency>
    <groupId>io.github.loom-ai-labs</groupId>
    <artifactId>ai-fabric-bom</artifactId>
    <version>0.4.0</version>
    <type>pom</type>
    <scope>import</scope>
  </dependency>
</dependencyManagement>`,
    },
    compatibility: [
      'Spring Boot applications',
      'Spring AI foundation',
      'Maven dependency management',
      'Provider and vector-store extension points',
    ],
    links: {
      primary: {
        label: 'Read the documentation',
        href: 'https://ai-fabric.dev',
        external: true,
      },
      source: {
        label: 'View on GitHub',
        href: 'https://github.com/Loom-AI-Labs/ai-fabric-framework',
        external: true,
      },
      documentation: {
        label: 'Developer guide',
        href: 'https://ai-fabric.dev',
        external: true,
      },
      releaseNotes: {
        label: '0.4.0 release',
        href: 'https://github.com/Loom-AI-Labs/ai-fabric-framework/releases/tag/ai-fabric-framework-v0.4.0',
        external: true,
      },
    },
    featured: true,
    sortOrder: 1,
  },
  {
    id: 'product:ai-fabric-chat-ui',
    slug: 'ai-fabric-chat-ui',
    name: 'AI Fabric Chat UI',
    shortName: 'Chat UI',
    layerLabel: 'User experience',
    statusLabel: 'Public beta',
    version: '0.3.0',
    licence: 'Apache 2.0',
    summary:
      'A framework-neutral presentation layer for evidence, clarification, confirmation and backend-owned AI conversations.',
    description:
      'AI Fabric Chat UI turns governed backend workflows into an application experience. It renders assistant messages, sources, forms and action states without becoming the identity, policy or execution boundary.',
    problem:
      'A plain message list cannot explain retrieval evidence, ask structured clarification, collect confirmation or present action outcomes with enough context for a dependable product experience.',
    value:
      'Chat UI supplies those reusable interaction surfaces while the host application keeps control of authentication, conversation persistence, policy and business operations.',
    capabilities: [
      {
        name: 'Conversation surfaces',
        description: 'Render backend-owned conversations in compact, full and embedded layouts.',
      },
      {
        name: 'Retrieval evidence',
        description: 'Present sources, documents and grounded answer context without raw payloads.',
      },
      {
        name: 'Structured clarification',
        description: 'Collect missing fields through explicit, application-friendly form contracts.',
      },
      {
        name: 'Action confirmation',
        description: 'Show proposed operations and capture deliberate user confirmation.',
      },
      {
        name: 'Host integration',
        description: 'Use Web Component or React bindings with Arabic and RTL support.',
      },
    ],
    owns: [
      'Rendering and interaction state for supported AI response contracts',
      'Accessible message, evidence, clarification and confirmation components',
      'Web Component and React integration surfaces',
      'Responsive and bidirectional presentation behavior',
    ],
    hostOwns: [
      'Authentication, session issuance and conversation authorization',
      'Backend requests, persistence and retry policy',
      'Business action permissions and side effects',
      'Product navigation, surrounding layout and customer support policy',
    ],
    flow: ['Host application', 'Chat UI surface', 'Backend conversation API', 'AI Fabric workflow'],
    quickStart: {
      language: 'html',
      code: `<script type="module" src="/ai-fabric-chat-ui.js"></script>

<ai-fabric-chat
  endpoint="/api/assistant/query"
  locale="en"
  mode="embedded">
</ai-fabric-chat>`,
    },
    compatibility: [
      'Web Component',
      'React',
      'Spring Boot backends',
      'Arabic and RTL layouts',
    ],
    links: {
      primary: {
        label: 'Explore live demo',
        href: 'https://ai-fabric.dev/demos/ai-fabric-framework',
        external: true,
      },
      source: {
        label: 'View on GitHub',
        href: 'https://github.com/Loom-AI-Labs/ai-fabric-chat-ui',
        external: true,
      },
      documentation: {
        label: 'Integration guide',
        href: 'https://github.com/Loom-AI-Labs/ai-fabric-chat-ui#readme',
        external: true,
      },
      releaseNotes: {
        label: 'Repository releases',
        href: 'https://github.com/Loom-AI-Labs/ai-fabric-chat-ui/releases',
        external: true,
      },
    },
    featured: true,
    sortOrder: 2,
  },
]

export const experiments: Experiment[] = [
  {
    id: 'experiment:ai-shopping-experience',
    slug: 'ai-shopping-experience',
    title: 'AI Shopping Experience',
    status: 'live',
    featured: true,
    summary:
      'A bounded commerce experience combining catalog retrieval, evidence, conversation state and governed interaction surfaces.',
    scenario:
      'A shopper explores synthetic products, asks grounded questions and inspects the evidence behind the response.',
    hypothesis:
      'Can one application make retrieval evidence and conversation controls visible without exposing its domain rules to the model or browser component?',
    primaryCategory: 'adaptive-experience',
    categoryLabel: 'Adaptive experience',
    capabilityTags: ['RAG', 'Evidence', 'Chat sessions', 'Data sync'],
    domainTags: ['Commerce', 'Customer-facing UI'],
    relatedProductSlugs: ['ai-fabric-framework', 'ai-fabric-chat-ui'],
    applicationControlBoundary:
      'The demo application owns catalog records, session behavior and customer-facing decisions. AI Fabric supplies orchestration contracts; Chat UI presents the result.',
    observableProof: [
      'The health surface reports AI Fabric 0.4.0 and enabled chat, retrieval and data-sync capabilities.',
      'The application exposes index readiness and retrieval proof instead of hiding an empty index.',
      'Conversation behavior remains backend-owned.',
    ],
    notDemonstrated: [
      'Customer adoption or production certification',
      'Real merchant catalog accuracy',
      'Autonomous purchasing or payment execution',
    ],
    guidedSteps: [
      'Open the live application and seed its synthetic catalog when prompted.',
      'Ask a product question that requires catalog evidence.',
      'Inspect the returned evidence and continue the same conversation.',
    ],
    runtimeStack: ['Spring Boot', 'AI Fabric 0.4.0', 'AI Fabric Chat UI', 'Lucene', 'OpenAI'],
    usesSyntheticData: true,
    dataNotice: 'The experiment uses synthetic catalog and conversation data.',
    knownLimitations: [
      'The catalog is intentionally small and synthetic.',
      'The live environment is an engineering demo, not a service-level commitment.',
    ],
    frameworkVersion: '0.4.0',
    lastVerified: '2026-07-25',
    screenshot: {
      src: '/assets/experiments/ai-shopping-experience.png',
      alt: 'AI Shopping Experience live demo interface',
    },
    links: {
      launch: {
        label: 'Launch experiment',
        href: 'https://ai-fabric.dev/demos/ai-fabric-framework',
        external: true,
      },
      source: {
        label: 'Inspect source',
        href: 'https://github.com/Loom-AI-Labs/ai-fabric-framework/tree/main/examples/real-apps/chat-capabilities-demo',
        external: true,
      },
    },
    sortOrder: 1,
  },
  {
    id: 'experiment:account-resolver',
    slug: 'account-resolver',
    title: 'Account Resolver',
    status: 'live',
    featured: true,
    summary:
      'A support workflow that separates model interpretation from validation, confirmation and application-owned account operations.',
    scenario:
      'A user asks for account help, reviews the proposed operation and confirms only after the application exposes validation evidence.',
    hypothesis:
      'Can confirmation stay useful when user-provided parameters are imperfect, while trusted resource identifiers remain protected?',
    primaryCategory: 'governed-actions',
    categoryLabel: 'Governed actions',
    capabilityTags: ['Actions', 'Validation', 'Confirmation', 'Audit'],
    domainTags: ['Subscription support', 'Operations'],
    relatedProductSlugs: ['ai-fabric-framework', 'ai-fabric-chat-ui'],
    applicationControlBoundary:
      'The application owns subscription state, validation policy, trusted identifiers and execution. The model proposes structured intent; it does not bypass those checks.',
    observableProof: [
      'Validation warnings can be shown without silently discarding user-confirmed values.',
      'Trusted resource identifiers remain blocking when provenance is not established.',
      'The final operation is performed by application code.',
    ],
    notDemonstrated: [
      'Real billing-provider integration',
      'Unattended financial operations',
      'Suitability for high-risk account changes',
    ],
    guidedSteps: [
      'Open a synthetic account scenario.',
      'Ask for a supported account change in natural language.',
      'Review validation evidence and confirm the proposed operation.',
    ],
    runtimeStack: ['Spring Boot', 'AI Fabric 0.4.0', 'Governed actions', 'OpenAI'],
    usesSyntheticData: true,
    dataNotice: 'All account, subscription and billing records are synthetic.',
    knownLimitations: [
      'The supported action catalog is deliberately narrow.',
      'No external payment provider is contacted.',
    ],
    frameworkVersion: '0.4.0',
    lastVerified: '2026-07-25',
    screenshot: {
      src: '/assets/experiments/account-resolver.png',
      alt: 'Account Resolver live demo interface',
    },
    links: {
      launch: {
        label: 'Launch experiment',
        href: 'https://ai-fabric.dev/demos/ai-fabric-account-resolver',
        external: true,
      },
      source: {
        label: 'Inspect source',
        href: 'https://github.com/Loom-AI-Labs/ai-fabric-framework/tree/main/examples/real-apps/ai-fabric-account-resolver',
        external: true,
      },
    },
    sortOrder: 2,
  },
  {
    id: 'experiment:behavior-signals',
    slug: 'behavior-signals',
    title: 'Behavior Signals',
    status: 'live',
    featured: false,
    summary:
      'An explainable churn-signal prototype that keeps scoring inputs and intervention rules inside the application.',
    scenario:
      'A product team inspects synthetic engagement signals, generated explanations and bounded follow-up suggestions.',
    hypothesis:
      'Can AI explain application-owned behavioral signals without becoming the source of truth for risk scoring?',
    primaryCategory: 'adaptive-experience',
    categoryLabel: 'Adaptive experience',
    capabilityTags: ['Structured output', 'Signals', 'Explanations', 'Actions'],
    domainTags: ['SaaS', 'Retention'],
    relatedProductSlugs: ['ai-fabric-framework'],
    applicationControlBoundary:
      'The host calculates behavioral signals and defines allowed interventions. AI Fabric structures explanations and proposed next steps.',
    observableProof: [
      'Signal values remain visible alongside generated explanations.',
      'Application policy constrains the available intervention actions.',
    ],
    notDemonstrated: ['Predictive model accuracy', 'Real customer retention outcomes'],
    guidedSteps: [
      'Select a synthetic customer profile.',
      'Inspect the application-owned signals.',
      'Generate an explanation and compare it with the source values.',
    ],
    runtimeStack: ['Spring Boot', 'AI Fabric 0.4.0', 'Structured generation', 'OpenAI'],
    usesSyntheticData: true,
    dataNotice: 'Customer and engagement records are synthetic.',
    knownLimitations: [
      'The signal model is illustrative, not statistically validated.',
      'No production customer data is processed.',
    ],
    frameworkVersion: '0.4.0',
    lastVerified: '2026-07-25',
    screenshot: {
      src: '/assets/experiments/behavior-signals.png',
      alt: 'Behavior Signals live demo interface',
    },
    links: {
      launch: {
        label: 'Launch experiment',
        href: 'https://ai-fabric.dev/demos/ai-fabric-behavior-signals',
        external: true,
      },
      source: {
        label: 'Inspect source',
        href: 'https://github.com/Loom-AI-Labs/ai-fabric-framework/tree/main/examples/real-apps/behavior-churn-signals',
        external: true,
      },
    },
    sortOrder: 3,
  },
  {
    id: 'experiment:tenant-guard',
    slug: 'tenant-guard',
    title: 'Tenant Guard',
    status: 'live',
    featured: false,
    summary:
      'A knowledge-portal scenario testing explicit tenant context, retrieval filters and application-owned access boundaries.',
    scenario:
      'Two synthetic tenants search related knowledge while the application carries identity and scope into retrieval.',
    hypothesis:
      'Can tenant identity remain an explicit orchestration input instead of being inferred from natural language?',
    primaryCategory: 'tenant-access',
    categoryLabel: 'Tenant access',
    capabilityTags: ['Tenant context', 'RAG', 'Filters', 'Evidence'],
    domainTags: ['Knowledge portal', 'Multi-tenant'],
    relatedProductSlugs: ['ai-fabric-framework'],
    applicationControlBoundary:
      'The host authenticates the caller, resolves tenant membership and provides trusted retrieval filters. The model never chooses tenant scope.',
    observableProof: [
      'Tenant identifiers are supplied as application context.',
      'Retrieval results remain attributable to the selected synthetic tenant.',
    ],
    notDemonstrated: ['A complete identity provider', 'Formal isolation certification'],
    guidedSteps: [
      'Choose the first synthetic tenant and run a knowledge query.',
      'Inspect the returned source ownership.',
      'Switch tenant context and repeat the query.',
    ],
    runtimeStack: ['Spring Boot', 'AI Fabric 0.4.0', 'Lucene', 'Tenant filters', 'OpenAI'],
    usesSyntheticData: true,
    dataNotice: 'Tenant memberships and knowledge records are synthetic.',
    knownLimitations: [
      'The demo identity switcher is not an authentication system.',
      'The dataset is intentionally compact.',
    ],
    frameworkVersion: '0.4.0',
    lastVerified: '2026-07-25',
    screenshot: {
      src: '/assets/experiments/tenant-guard.png',
      alt: 'Tenant Guard live demo interface',
    },
    links: {
      launch: {
        label: 'Launch experiment',
        href: 'https://ai-fabric.dev/demos/ai-fabric-tenant-guard',
        external: true,
      },
      source: {
        label: 'Inspect source',
        href: 'https://github.com/Loom-AI-Labs/ai-fabric-framework/tree/main/examples/real-apps/tenant-knowledge-portal',
        external: true,
      },
    },
    sortOrder: 4,
  },
  {
    id: 'experiment:privacy-shield',
    slug: 'privacy-shield',
    title: 'Privacy Shield',
    status: 'live',
    featured: false,
    summary:
      'A local-first support prototype that prepares privacy-aware context before retrieval or generation.',
    scenario:
      'A support message containing synthetic personal data is detected, redacted and audited before downstream processing.',
    hypothesis:
      'Can privacy preparation become a visible application stage rather than an invisible prompt instruction?',
    primaryCategory: 'privacy-security',
    categoryLabel: 'Privacy and security',
    capabilityTags: ['PII handling', 'Redaction', 'Audit', 'Local processing'],
    domainTags: ['Customer support', 'Privacy'],
    relatedProductSlugs: ['ai-fabric-framework'],
    applicationControlBoundary:
      'The application selects detection mode, encryption posture and downstream policy. The demo intentionally does not require a remote model.',
    observableProof: [
      'Input and output detection direction is configurable.',
      'Redaction and audit behavior can be inspected without an OpenAI key.',
    ],
    notDemonstrated: ['Legal compliance', 'Coverage of every identifier or jurisdiction'],
    guidedSteps: [
      'Submit the provided synthetic support message.',
      'Compare detected values with the prepared context.',
      'Inspect the local audit result.',
    ],
    runtimeStack: ['Spring Boot', 'AI Fabric 0.4.0', 'Local detection', 'Local embeddings'],
    usesSyntheticData: true,
    dataNotice: 'The sample personal identifiers are synthetic and disposable.',
    knownLimitations: [
      'Pattern detection can miss novel identifiers.',
      'The demo is not a legal or compliance assessment.',
    ],
    frameworkVersion: '0.4.0',
    lastVerified: '2026-07-25',
    screenshot: {
      src: '/assets/experiments/privacy-shield.png',
      alt: 'Privacy Shield live demo interface',
    },
    links: {
      launch: {
        label: 'Launch experiment',
        href: 'https://ai-fabric.dev/demos/ai-fabric-privacy-shield',
        external: true,
      },
      source: {
        label: 'Inspect source',
        href: 'https://github.com/Loom-AI-Labs/ai-fabric-framework/tree/main/examples/real-apps/privacy-first-customer-facing-support',
        external: true,
      },
    },
    sortOrder: 5,
  },
  {
    id: 'experiment:live-data-sync',
    slug: 'live-data-sync',
    title: 'Live Data Sync',
    status: 'live',
    featured: true,
    summary:
      'A data-lifecycle workbench showing entity changes, queued indexing, retrieval proof and source/index alignment.',
    scenario:
      'An operator creates, updates and removes synthetic application entities while watching the AI index converge.',
    hypothesis:
      'Can indexing lifecycle state be observable enough to diagnose stale or missing AI evidence?',
    primaryCategory: 'data-retrieval',
    categoryLabel: 'Data and retrieval',
    capabilityTags: ['Data sync', 'Index lifecycle', 'Retrieval proof', 'Operations'],
    domainTags: ['Data operations', 'RAG'],
    relatedProductSlugs: ['ai-fabric-framework'],
    applicationControlBoundary:
      'The application database remains the source of truth. AI Fabric receives explicit lifecycle operations and exposes index evidence.',
    observableProof: [
      'Create, update and delete operations have visible indexing state.',
      'Retrieval proof checks whether a known entity is actually searchable.',
      'The operator can distinguish source count, queue state and vector count.',
    ],
    notDemonstrated: ['Unlimited ingestion throughput', 'Zero-latency consistency'],
    guidedSteps: [
      'Seed the synthetic source records.',
      'Run the synchronization operation and inspect queue progress.',
      'Use retrieval proof, then update and remove a record.',
    ],
    runtimeStack: ['Spring Boot', 'AI Fabric 0.4.0', 'Lucene', 'OpenAI embeddings'],
    usesSyntheticData: true,
    dataNotice: 'All source entities are generated for the experiment.',
    knownLimitations: [
      'The single-node demo does not model every distributed failure mode.',
      'Throughput figures are not presented as benchmarks.',
    ],
    frameworkVersion: '0.4.0',
    lastVerified: '2026-07-25',
    screenshot: {
      src: '/assets/experiments/live-data-sync.png',
      alt: 'Live Data Sync live demo interface',
    },
    links: {
      launch: {
        label: 'Launch experiment',
        href: 'https://ai-fabric.dev/demos/ai-fabric-live-data-sync',
        external: true,
      },
      source: {
        label: 'Inspect source',
        href: 'https://github.com/Loom-AI-Labs/ai-fabric-framework/tree/main/examples/real-apps/ai-fabric-live-data-sync',
        external: true,
      },
    },
    sortOrder: 6,
  },
]

export const research: ResearchArtifact[] = [
  {
    id: 'research:application-data-ai-evidence-alignment',
    slug: 'application-data-ai-evidence-alignment',
    title: 'Keeping Application Data and AI Evidence Aligned',
    researchQuestion:
      'How can an application make entity-to-index consistency visible and recoverable as source records change?',
    abstract:
      'This investigation treats AI indexing as an application data lifecycle rather than a one-time ingestion task. It links source mutations, queued operations, vector state and retrieval proof so an operator can inspect where alignment failed.',
    theme: 'data-consistency',
    themeLabel: 'Data consistency',
    maturityLabel: 'Demo verified',
    artifactType: 'Reproducible evaluation',
    evidenceLevel: 'reproducible-demo',
    evidenceLabel: 'Reproducible demo',
    featured: true,
    context:
      'Grounded answers become unreliable when application records and retrieval indexes drift. A successful API response alone does not establish that a newly indexed entity can be found, or that a deleted entity has disappeared.',
    proposedApproach: [
      'Model create, update and delete as explicit entity lifecycle operations.',
      'Expose source counts, queue state, vector counts and a known-entity retrieval probe.',
      'Keep the application database authoritative and make replay safe.',
    ],
    implementation: [
      'AI Fabric 0.4.0 entity indexing contracts',
      'A Spring Boot reference application with persistent source records',
      'A live readiness surface covering source, queue and retrieval proof',
    ],
    keyObservations: [
      'A vector count is useful but insufficient; a known-entity probe reveals routing and metadata failures.',
      'Delete semantics need the same operational evidence as create and update.',
      'A visible empty stage prevents a demo from implying retrieval readiness before data exists.',
    ],
    limitations: [
      'The live proof uses a single application instance and a compact synthetic dataset.',
      'The work does not publish throughput or durability benchmarks.',
    ],
    nextQuestions: [
      'How should repair plans be generated across distributed indexing workers?',
      'Which readiness evidence belongs in framework APIs versus platform operations?',
    ],
    implementationArtifacts: [
      {
        label: 'Launch Live Data Sync',
        href: 'https://ai-fabric.dev/demos/ai-fabric-live-data-sync',
        external: true,
      },
      {
        label: 'Inspect reference source',
        href: 'https://github.com/Loom-AI-Labs/ai-fabric-framework/tree/main/examples/real-apps/ai-fabric-live-data-sync',
        external: true,
      },
    ],
    linkedExperimentSlugs: ['live-data-sync'],
    linkedProductSlugs: ['ai-fabric-framework'],
    frameworkVersion: '0.4.0',
    authorsOrContributors: ['Loom AI Labs maintainers'],
    updatedAt: '2026-07-25',
    readingTime: '7 min',
    sortOrder: 1,
  },
  {
    id: 'research:explicit-application-context',
    slug: 'explicit-application-context',
    title: 'Explicit Application Context Instead of Model Guessing',
    researchQuestion:
      'Which request context must be resolved by application code before AI orchestration begins?',
    abstract:
      'This study separates trusted application context from model-extracted language. It focuses on tenant identity, allowed vector spaces and resource scope as explicit inputs rather than values inferred from a prompt.',
    theme: 'context-grounding',
    themeLabel: 'Context and grounding',
    maturityLabel: 'Implemented prototype',
    artifactType: 'Architecture study',
    evidenceLevel: 'implemented-prototype',
    evidenceLabel: 'Implemented prototype',
    featured: true,
    context:
      'Natural language can mention a tenant, domain or entity type, but mention is not authority. Retrieval quality and isolation both degrade when inferred labels overwrite valid application context.',
    proposedApproach: [
      'Resolve identity and tenant membership in the host application.',
      'Promote trusted vector-space and entity hints into orchestration context.',
      'Allow model extraction to clarify user intent without replacing trusted scope.',
    ],
    implementation: [
      'Request-context propagation through AI Fabric orchestration',
      'Vector-space resolution that honors valid application hints',
      'A multi-tenant knowledge portal demonstrating filtered retrieval',
    ],
    keyObservations: [
      'Trusted context and model-extracted intent serve different purposes and should remain distinguishable.',
      'A valid application hint can prevent an unrelated extracted label from sending retrieval to an empty space.',
    ],
    limitations: [
      'The demo tenant selector is not an authentication provider.',
      'The study does not claim formal tenant-isolation certification.',
    ],
    nextQuestions: [
      'How should context provenance be represented across remote connectors?',
      'Which context fields should be immutable after orchestration starts?',
    ],
    implementationArtifacts: [
      {
        label: 'Launch Tenant Guard',
        href: 'https://ai-fabric.dev/demos/ai-fabric-tenant-guard',
        external: true,
      },
      {
        label: 'Inspect framework source',
        href: 'https://github.com/Loom-AI-Labs/ai-fabric-framework',
        external: true,
      },
    ],
    linkedExperimentSlugs: ['tenant-guard'],
    linkedProductSlugs: ['ai-fabric-framework'],
    frameworkVersion: '0.4.0',
    authorsOrContributors: ['Loom AI Labs maintainers'],
    updatedAt: '2026-07-25',
    readingTime: '6 min',
    sortOrder: 2,
  },
  {
    id: 'research:governed-ai-proposed-actions',
    slug: 'governed-ai-proposed-actions',
    title: 'A Governed Lifecycle for AI-Proposed Actions',
    researchQuestion:
      'How can an application accept useful model-proposed parameters without weakening trusted resource and execution boundaries?',
    abstract:
      'This investigation follows an action from model interpretation through validation, warning, confirmation and application execution. It distinguishes advisory validation from trusted target checks.',
    theme: 'actions-governance',
    themeLabel: 'Actions and governance',
    maturityLabel: 'Demo verified',
    artifactType: 'Reference implementation',
    evidenceLevel: 'reproducible-demo',
    evidenceLabel: 'Reproducible demo',
    featured: true,
    context:
      'Strict validation can make conversational actions brittle, but permissive execution can turn model interpretation into authority. The useful middle is explicit provenance plus a confirmation contract.',
    proposedApproach: [
      'Parse natural-language values into a typed proposal.',
      'Treat configurable business validation as warn or block policy.',
      'Always block untrusted hidden or system-owned resource identifiers.',
      'Require application execution to repeat final authorization.',
    ],
    implementation: [
      'Configurable confirmation validation mode',
      'Parameter provenance in action results',
      'A live synthetic subscription-resolution workflow',
    ],
    keyObservations: [
      'Warnings preserve useful user intent while keeping uncertainty visible at confirmation.',
      'Trusted identifiers require a stronger rule because the user cannot safely validate hidden targets.',
      'Confirmation is a workflow state, not an LLM call.',
    ],
    limitations: [
      'The demo does not connect to a real billing provider.',
      'High-risk financial or irreversible actions need additional policy controls.',
    ],
    nextQuestions: [
      'How should policy services contribute signed validation evidence?',
      'Which actions require dual control or delayed execution?',
    ],
    implementationArtifacts: [
      {
        label: 'Launch Account Resolver',
        href: 'https://ai-fabric.dev/demos/ai-fabric-account-resolver',
        external: true,
      },
      {
        label: 'Inspect reference source',
        href: 'https://github.com/Loom-AI-Labs/ai-fabric-framework/tree/main/examples/real-apps/ai-fabric-account-resolver',
        external: true,
      },
    ],
    linkedExperimentSlugs: ['account-resolver'],
    linkedProductSlugs: ['ai-fabric-framework', 'ai-fabric-chat-ui'],
    frameworkVersion: '0.4.0',
    authorsOrContributors: ['Loom AI Labs maintainers'],
    updatedAt: '2026-07-25',
    readingTime: '8 min',
    sortOrder: 3,
  },
  {
    id: 'research:tenant-identity-orchestration-context',
    slug: 'tenant-identity-orchestration-context',
    title: 'Tenant Identity as Orchestration Context',
    researchQuestion:
      'How should tenant identity cross retrieval and action boundaries without becoming prompt text?',
    abstract:
      'A focused security investigation into carrying resolved tenant identity as trusted orchestration metadata and applying it again at data and operation boundaries.',
    theme: 'privacy-identity',
    themeLabel: 'Privacy and identity',
    maturityLabel: 'Ongoing evaluation',
    artifactType: 'Security investigation',
    evidenceLevel: 'implemented-prototype',
    evidenceLabel: 'Implemented prototype',
    featured: false,
    context:
      'Tenant names in user text are ambiguous and forgeable. Multi-tenant AI features need the same resolved identity and authorization context as ordinary application features.',
    proposedApproach: [
      'Authenticate and resolve membership before invoking AI orchestration.',
      'Carry a trusted tenant key separately from conversational text.',
      'Reapply tenant filters at retrieval and action execution.',
    ],
    implementation: [
      'Tenant-aware request context in a reference application',
      'Filtered retrieval with source ownership visible in results',
      'Tests for cross-tenant query behavior',
    ],
    keyObservations: [
      'Prompt content is useful for intent but unsuitable as the tenant authority.',
      'Source metadata makes boundary failures easier to observe during development.',
    ],
    limitations: [
      'The prototype does not replace application authorization testing.',
      'The current public demo uses a simplified identity switcher.',
    ],
    nextQuestions: [
      'How should tenant context be signed across service boundaries?',
      'What audit evidence is required for shared vector backends?',
    ],
    implementationArtifacts: [
      {
        label: 'Inspect Tenant Guard',
        href: 'https://github.com/Loom-AI-Labs/ai-fabric-framework/tree/main/examples/real-apps/tenant-knowledge-portal',
        external: true,
      },
    ],
    linkedExperimentSlugs: ['tenant-guard'],
    linkedProductSlugs: ['ai-fabric-framework'],
    frameworkVersion: '0.4.0',
    authorsOrContributors: ['Loom AI Labs maintainers'],
    updatedAt: '2026-07-25',
    readingTime: '5 min',
    sortOrder: 4,
  },
  {
    id: 'research:privacy-aware-rag-context',
    slug: 'privacy-aware-rag-context',
    title: 'Privacy-Aware Context Preparation for RAG',
    researchQuestion:
      'Where should detection, redaction and encrypted-original handling occur before retrieval or generation?',
    abstract:
      'This investigation treats privacy preparation as an application-visible stage with configurable direction, storage posture and audit evidence.',
    theme: 'privacy-identity',
    themeLabel: 'Privacy and identity',
    maturityLabel: 'Implemented prototype',
    artifactType: 'Security investigation',
    evidenceLevel: 'implemented-prototype',
    evidenceLabel: 'Implemented prototype',
    featured: false,
    context:
      'A prompt instruction to ignore personal data does not remove that data from provider requests or logs. Context should be prepared before it enters retrieval and generation paths.',
    proposedApproach: [
      'Detect candidate personal information at explicit input and output boundaries.',
      'Redact provider-facing context according to application policy.',
      'Store encrypted originals only when the use case requires them.',
    ],
    implementation: [
      'Configurable local PII preparation in a Spring Boot demo',
      'Redacted context and audit output visible side by side',
      'A no-remote-model mode for deterministic inspection',
    ],
    keyObservations: [
      'Visible preparation stages are easier to test than prompt-only privacy claims.',
      'Local processing supports useful demonstrations without sending sample identifiers to a model provider.',
    ],
    limitations: [
      'Pattern-based detection does not cover every identifier.',
      'The implementation is a technical control, not a compliance determination.',
    ],
    nextQuestions: [
      'How should organization-specific detectors be composed and evaluated?',
      'What retention policies should apply to encrypted originals?',
    ],
    implementationArtifacts: [
      {
        label: 'Launch Privacy Shield',
        href: 'https://ai-fabric.dev/demos/ai-fabric-privacy-shield',
        external: true,
      },
      {
        label: 'Inspect reference source',
        href: 'https://github.com/Loom-AI-Labs/ai-fabric-framework/tree/main/examples/real-apps/privacy-first-customer-facing-support',
        external: true,
      },
    ],
    linkedExperimentSlugs: ['privacy-shield'],
    linkedProductSlugs: ['ai-fabric-framework'],
    frameworkVersion: '0.4.0',
    authorsOrContributors: ['Loom AI Labs maintainers'],
    updatedAt: '2026-07-25',
    readingTime: '6 min',
    sortOrder: 5,
  },
]

export const relations: ContentRelation[] = [
  {
    from: 'product:ai-fabric-chat-ui',
    to: 'product:ai-fabric-framework',
    type: 'complements',
    label: 'Presents governed workflow contracts',
  },
  ...experiments.flatMap((experiment) =>
    experiment.relatedProductSlugs.map(
      (slug): ContentRelation => ({
        from: experiment.id,
        to: `product:${slug}`,
        type: 'uses',
      }),
    ),
  ),
  ...research.flatMap((item) => [
    ...item.linkedProductSlugs.map(
      (slug): ContentRelation => ({
        from: item.id,
        to: `product:${slug}`,
        type: 'informs',
      }),
    ),
    ...item.linkedExperimentSlugs.map(
      (slug): ContentRelation => ({
        from: item.id,
        to: `experiment:${slug}`,
        type: 'explains',
      }),
    ),
  ]),
]

export const experimentCategories = [
  { value: 'all', label: 'All experiments' },
  { value: 'data-retrieval', label: 'Data and retrieval' },
  { value: 'governed-actions', label: 'Governed actions' },
  { value: 'privacy-security', label: 'Privacy and security' },
  { value: 'tenant-access', label: 'Tenant access' },
  { value: 'adaptive-experience', label: 'Adaptive experience' },
] as const

export const researchThemes = [
  { value: 'all', label: 'All research' },
  { value: 'data-consistency', label: 'Data consistency' },
  { value: 'context-grounding', label: 'Context and grounding' },
  { value: 'actions-governance', label: 'Actions and governance' },
  { value: 'privacy-identity', label: 'Privacy and identity' },
  { value: 'developer-experience', label: 'Developer experience' },
] as const

export function getProduct(slug: string) {
  return products.find((item) => item.slug === slug)
}

export function getExperiment(slug: string) {
  return experiments.find((item) => item.slug === slug)
}

export function getResearch(slug: string) {
  return research.find((item) => item.slug === slug)
}

export function getRelatedExperiments(productSlug: string) {
  return experiments.filter((item) => item.relatedProductSlugs.includes(productSlug))
}

export function getRelatedResearch(productSlug: string) {
  return research.filter((item) => item.linkedProductSlugs.includes(productSlug))
}

export function validateContentGraph() {
  const errors: string[] = []
  const knownIds = new Set([
    ...products.map((item) => item.id),
    ...experiments.map((item) => item.id),
    ...research.map((item) => item.id),
  ])
  const relationKeys = new Set<string>()

  for (const relation of relations) {
    const key = `${relation.from}:${relation.type}:${relation.to}`
    if (relation.from === relation.to) {
      errors.push(`Self relation is not allowed: ${key}`)
    }
    if (!knownIds.has(relation.from) || !knownIds.has(relation.to)) {
      errors.push(`Unknown relation target: ${key}`)
    }
    if (relationKeys.has(key)) {
      errors.push(`Duplicate relation: ${key}`)
    }
    relationKeys.add(key)
  }

  for (const experiment of experiments) {
    if (experiment.relatedProductSlugs.length === 0) {
      errors.push(`${experiment.id} must use at least one product`)
    }
  }
  return errors
}
