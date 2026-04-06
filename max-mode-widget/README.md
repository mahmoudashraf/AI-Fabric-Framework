# @anthropic/max-mode-widget

Embeddable AI shopping assistant widget. Drop it into **any website** — plain HTML, React apps, Shopify stores, WordPress — with a single script tag or npm install.

## Quick Start

### Option 1: Script Tag (any website)

```html
<script src="https://mahmoudashraf.github.io/aifabric/max-mode-widget.iife.js"></script>
<script>
  MaxMode.init({
    apiConfig: {
      chatBaseUrl: "https://your-api.com/api",
      crudBaseUrl: "https://your-crud-api.com/api",
      headers: { "X-API-KEY": "your-key" },
    },
    userId: "user_123",
    theme: { primaryColor: "#6366f1" },
  });
</script>
```

That's it. A floating chat button appears in the bottom-right corner.

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
          chatBaseUrl: "https://your-api.com/api",
          crudBaseUrl: "https://your-crud-api.com/api",
          headers: { "X-API-KEY": "your-key" },
        }}
        userId="user_123"
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
    crudBaseUrl: string;       // CRUD API (cart, conversations)
    headers?: Record<string, string>;
  };
  userId?: string;
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
cd packages/max-mode-widget
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
