# @anthropic/max-mode-widget

Embeddable AI shopping assistant widget. Drop it into **any website** — plain HTML, React apps, Shopify stores, WordPress — with a single script tag or npm install.

For storefront/customer integration auth modes, see [docs/WIDGET_AUTH_MODES_AND_CUSTOMER_INTEGRATION_PLAN.md](docs/WIDGET_AUTH_MODES_AND_CUSTOMER_INTEGRATION_PLAN.md).

## Quick Start

### Option 1: Script Tag (backend-mediated private runtime, recommended)

```html
<script src="https://mahmoudashraf.github.io/AI-Fabric-Framework/max-mode-widget.iife.js"></script>
<script>
  MaxMode.init({
    apiConfig: {
      chatBaseUrl: "https://your-storefront.example.com/ai",
    },
    integrationMode: "backend-mediated-private-runtime",
    features: { cart: false },
    theme: { primaryColor: "#6366f1" },
  });
</script>
```

That's it. A floating chat button appears in the bottom-right corner.

### Option 1B: Script Tag (public runtime anonymous, opt-in)

```html
<script src="https://mahmoudashraf.github.io/AI-Fabric-Framework/max-mode-widget.iife.js"></script>
<script>
  MaxMode.init({
    apiConfig: {
      chatBaseUrl: "https://your-runtime.example.com/api",
      runtimeRoutes: {
        chatQueryUrl: "https://your-runtime.example.com/api/chat/me/query",
        suggestionsUrl: "https://your-runtime.example.com/api/chat/me/suggestions",
        conversationsUrl: "https://your-runtime.example.com/api/chat/me/conversations",
        conversationItemUrlTemplate:
          "https://your-runtime.example.com/api/chat/me/conversations/{conversationId}",
        authContextUrl: "https://your-runtime.example.com/api/chat/me/auth-context",
      },
      runtimeAuth: {
        bootstrapUrl: "https://your-runtime.example.com/api/public/chat/session",
      },
    },
    integrationMode: "public-runtime-anonymous",
    features: { cart: false },
  });
</script>
```

### Option 2: npm (React apps)

```bash
npm install @anthropic/max-mode-widget
```

```tsx
import { MaxModeWidget, useMaxMode } from "@anthropic/max-mode-widget";
import "@anthropic/max-mode-widget/styles.css";

function App() {
  const { isOpen, open, close } = useMaxMode();

  return (
    <>
      <button onClick={open}>Open AI Assistant</button>
      <MaxModeWidget
        isOpen={isOpen}
        onClose={close}
        apiConfig={{
          chatBaseUrl: "https://your-runtime.example.com/api",
          runtimeRoutes: {
            chatQueryUrl: "https://your-runtime.example.com/api/chat/me/query",
            suggestionsUrl: "https://your-runtime.example.com/api/chat/me/suggestions",
            conversationsUrl: "https://your-runtime.example.com/api/chat/me/conversations",
            conversationItemUrlTemplate:
              "https://your-runtime.example.com/api/chat/me/conversations/{conversationId}",
            authContextUrl: "https://your-runtime.example.com/api/chat/me/auth-context",
          },
          runtimeAuth: {
            getBearerToken: async () => window.sessionStorage.getItem("maxmode-token"),
          },
        }}
        integrationMode="public-runtime-authenticated"
        features={{ cart: false }}
        theme={{ primaryColor: "#6366f1" }}
      />
    </>
  );
}
```

### Option 3: Shopify

Add `max-mode-widget.iife.js` to your theme assets, then add the Liquid snippet to `theme.liquid`:

```liquid
{% include 'max-mode-widget' %}
```

See `examples/shopify/snippet.liquid` for the full integration.
That example now defaults to the recommended backend-mediated private-runtime posture rather than browser-held static credentials.

---

## API Reference

### Script Tag API (`window.MaxMode`)

| Method | Description |
|--------|-------------|
| `MaxMode.init(config)` | Initialize and mount the widget |
| `MaxMode.open()` | Open the widget |
| `MaxMode.close()` | Close the widget |
| `MaxMode.toggle()` | Toggle open/closed |
| `MaxMode.attachProduct({ sku, name, price })` | Pre-attach a product to chat |
| `MaxMode.sendMessage(text)` | Send a message programmatically |
| `MaxMode.destroy()` | Unmount and clean up |

### React API

| Export | Description |
|--------|-------------|
| `<MaxModeWidget />` | Main widget component |
| `<MaxModeProvider />` | Context provider (for advanced usage) |
| `useMaxMode()` | Programmatic control hook |

### Configuration

```ts
interface MaxModeWidgetConfig {
  apiConfig: {
    chatBaseUrl: string;       // Chat/orchestration API
    crudBaseUrl?: string;      // Optional business CRUD API (cart/orders)
    runtimeRoutes?: {
      chatQueryUrl?: string;                // Optional absolute URL or path
      suggestionsUrl?: string;              // Optional absolute URL or path
      conversationsUrl?: string;            // Optional absolute URL or path
      conversationItemUrlTemplate?: string; // Use {conversationId} placeholder when possible
      authContextUrl?: string;              // Optional absolute URL or path
    };
    headers?: Record<string, string>;
    chatHeaders?: Record<string, string>;
    crudHeaders?: Record<string, string>;
      runtimeAuth?: {
        authorizationHeader?: string;
        tokenScheme?: string;
        bootstrapUrl?: string;
        authContextUrl?: string; // legacy compatibility override; prefer apiConfig.runtimeRoutes.authContextUrl
        probeAuthContextOnOpen?: boolean;
        getBearerToken?: () => Promise<string | null | undefined> | string | null | undefined;
        bootstrapAnonymous?: (request: { sessionId?: string }) => Promise<{
          token: string;
        tokenType?: string;
        authMode?: string;
        subjectType?: string;
        sessionId?: string;
        expiresAt?: string;
      }>;
    };
  };
  integrationMode?:
    | "backend-mediated-private-runtime"
    | "public-runtime-authenticated"
    | "public-runtime-anonymous"
    | "legacy-static-header";
  userId?: string;             // Legacy static-header mode only
  sessionId?: string;          // Legacy static-header mode, or anonymous bootstrap hint
  position?: "bottom-right" | "bottom-left";
  launcher?: boolean;          // Show floating button (default: true)
  features?: {
    cart?: boolean;            // Shopping cart (default: true)
    debug?: boolean;           // Debug inspector (default: false)
    conversations?: boolean;   // Conversation history (default: true)
    quickActions?: boolean;    // Quick action buttons (default: true)
  };
  theme?: {
    primaryColor?: string;     // Hex color (e.g. "#6366f1")
    borderRadius?: string;     // CSS value (default: "0.5rem")
    fontFamily?: string;       // CSS font stack
    darkMode?: boolean | "auto";
  };
  onEvent?: (event: MaxModeEvent) => void;
  onClose?: () => void;
}
```

`crudBaseUrl` is optional for secure chat-only integrations.

- Chat, auth bootstrap, auth-context, suggestions, and secure `/chat/me/*` conversation routes use `chatBaseUrl`.
- Business CRUD such as carts still require `crudBaseUrl`.
- If `crudBaseUrl` is omitted, the widget automatically disables cart/business CRUD UI instead of falling back to `chatBaseUrl`.

For secure integrations, prefer passing route-level metadata from your platform/customer integration contract into `apiConfig.runtimeRoutes` rather than inferring secure paths from `chatBaseUrl` alone.

If you already have platform integration metadata such as:

- `preferredChatQueryUrl`
- `preferredSuggestionsUrl`
- `preferredConversationsUrl`
- `preferredConversationItemUrlTemplate`

map those fields directly into:

- `apiConfig.runtimeRoutes.chatQueryUrl`
- `apiConfig.runtimeRoutes.suggestionsUrl`
- `apiConfig.runtimeRoutes.conversationsUrl`
- `apiConfig.runtimeRoutes.conversationItemUrlTemplate`

For the secure integration modes, the widget probes the runtime auth context on open by default. Use that to confirm the effective runtime posture:

- `backend-mediated-private-runtime` -> `PRIVATE_RUNTIME_BACKEND_MEDIATED`
- `public-runtime-authenticated` -> `PUBLIC_RUNTIME_AUTHENTICATED`
- `public-runtime-anonymous` -> `PUBLIC_RUNTIME_ANONYMOUS`

If your runtime or proxy returns legacy compatibility identity instead, the widget raises an immediate error event so misconfigured auth does not stay silent.

### Events

Subscribe to widget events via the `onEvent` callback:

| Event | Data | When |
|-------|------|------|
| `widget:opened` | — | Widget opens |
| `widget:closed` | — | Widget closes |
| `message:sent` | `{ content }` | User sends a message |
| `message:received` | `{ content, resultType }` | AI responds |
| `cart:add` | `{ product }` | Item added to cart |
| `cart:remove` | `{ product }` | Item removed from cart |

---

## Architecture

```
src/
├── entries/
│   ├── iife.ts           # window.MaxMode (script tag)
│   ├── react.ts          # npm exports
│   ├── MaxModeWidget.tsx  # React wrapper component
│   └── WidgetShell.tsx    # IIFE shell with launcher button
├── mount.ts              # Shadow DOM mount system
├── config.ts             # Runtime configuration singleton
├── theme.ts              # CSS custom properties engine
├── context.ts            # Self-contained React context
├── constants.ts          # Quick actions, categories
├── types.ts              # TypeScript interfaces
├── ui/                   # Internalized UI primitives
├── components/           # All widget components
├── hooks/                # State management hooks
├── api/                  # API client layer
├── integrations/
│   └── shopify.ts        # Shopify Cart API + product detection
└── styles/
    └── index.css         # Tailwind + CSS custom properties
```

### Key Design Decisions

- **Shadow DOM isolation** (IIFE build) — no CSS conflicts with host site
- **Tailwind prefix** (`mxw-`) — prevents class collisions
- **Config-driven API URLs** — no hardcoded endpoints
- **Bundled React** in IIFE — zero dependencies for script-tag users
- **Tree-shakeable ESM** — npm users only bundle what they use

---

## Development

```bash
cd max-mode-widget
npm install
npm run dev       # Watch mode
npm run build     # Production build (ESM + IIFE)
npm run typecheck # TypeScript validation
```

### Build Outputs

| File | Format | Size | Use Case |
|------|--------|------|----------|
| `dist/max-mode-widget.esm.js` | ESM | ~120KB | npm/React apps |
| `dist/max-mode-widget.cjs.js` | CJS | ~120KB | Node/SSR |
| `dist/max-mode-widget.iife.js` | IIFE | ~350KB | Script tag (includes React) |
| `dist/styles.css` | CSS | ~25KB | ESM consumers |

## License

MIT
