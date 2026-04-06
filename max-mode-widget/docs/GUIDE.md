# Max Mode Widget - Developer & User Guide

> Embeddable AI shopping assistant widget. Drop it into any website with a single `<script>` tag, or import it as an npm package in React apps. Works on plain HTML sites, Shopify stores, WordPress, Wix, and any platform that supports JavaScript.

---

## Table of Contents

1. [Overview](#1-overview)
2. [Installation](#2-installation)
3. [Quick Start](#3-quick-start)
4. [Configuration Reference](#4-configuration-reference)
5. [Integration Guides](#5-integration-guides)
6. [React API Reference](#6-react-api-reference)
7. [Script Tag API Reference](#7-script-tag-api-reference)
8. [Theming & Customization](#8-theming--customization)
9. [Events & Analytics](#9-events--analytics)
10. [Shopify Integration](#10-shopify-integration)
11. [Architecture Deep Dive](#11-architecture-deep-dive)
12. [Development Guide](#12-development-guide)
13. [Troubleshooting](#13-troubleshooting)
14. [FAQ](#14-faq)

---

## 1. Overview

### What is Max Mode Widget?

Max Mode Widget is a production-ready, embeddable AI-powered shopping assistant that provides:

- **Conversational AI chat** with product search, recommendations, and order management
- **Shopping cart management** with add/remove/checkout flows
- **Document retrieval (RAG)** with similarity scoring and source citation
- **Conversation history** with persistence across page reloads
- **Quick actions** for common tasks (search, browse, track orders, etc.)
- **Debug inspector** for API request/response monitoring
- **Responsive layout** optimized for both desktop and mobile

### How It Works

The widget connects to your AI Fabric backend via two API endpoints:

```
chatBaseUrl  ->  Chat orchestration engine (queries, suggestions, conversations)
crudBaseUrl  ->  CRUD operations (cart, products, orders)
```

You provide these URLs when initializing the widget. The widget handles all UI rendering, state management, and API communication.

### Distribution Formats

| Format | File | Size (gzipped) | Use Case |
|--------|------|----------------|----------|
| **IIFE** | `max-mode-widget.iife.js` | ~192 KB | `<script>` tag on any website. React is bundled inside. |
| **ESM** | `max-mode-widget.esm.js` | ~219 KB | `import` in React/Vue/Svelte apps via npm. |
| **CJS** | `max-mode-widget.cjs.js` | ~144 KB | `require()` in Node.js / SSR environments. |
| **CSS** | `style.css` | ~1.3 KB | Stylesheet for ESM/CJS consumers. |
| **Types** | `*.d.ts` | -- | Full TypeScript declarations. |

---

## 2. Installation

### Option A: Script Tag (any website)

No build tools required. Add two lines before `</body>`:

```html
<script src="https://mahmoudashraf.github.io/aifabric/max-mode-widget.iife.js"></script>
<script>
  MaxMode.init({
    apiConfig: {
      chatBaseUrl: "https://your-api.com/api",
      crudBaseUrl: "https://your-crud-api.com/api",
      headers: { "X-AIFABRIC-API-KEY": "your-key" },
    },
  });
</script>
```

### Option B: npm (React / Next.js / Vite)

```bash
npm install @anthropic/max-mode-widget
```

```tsx
import { MaxModeWidget, useMaxMode } from "@anthropic/max-mode-widget";
import "@anthropic/max-mode-widget/styles.css";
```

### Option C: Self-Hosted

Download the `dist/` folder from the package and serve the files from your own CDN or static file server.

---

## 3. Quick Start

### Minimal HTML Example

```html
<!DOCTYPE html>
<html>
<head><title>My Store</title></head>
<body>
  <h1>Welcome to My Store</h1>

  <script src="max-mode-widget.iife.js"></script>
  <script>
    MaxMode.init({
      apiConfig: {
        chatBaseUrl: "https://rest-connector-dep-1bf14c33-dev.up.railway.app/api",
        crudBaseUrl: "https://ai-fabric-framework-production-a247.up.railway.app/api",
        headers: { "X-AIFABRIC-API-KEY": "test" },
      },
      userId: "visitor-123",
    });
  </script>
</body>
</html>
```

A purple floating chat button appears in the bottom-right corner. Click it to open the full AI assistant.

### Minimal React Example

```tsx
import { useState } from "react";
import { MaxModeWidget } from "@anthropic/max-mode-widget";
import "@anthropic/max-mode-widget/styles.css";

function App() {
  const [open, setOpen] = useState(false);

  return (
    <>
      <button onClick={() => setOpen(true)}>Chat with AI</button>
      <MaxModeWidget
        isOpen={open}
        onClose={() => setOpen(false)}
        apiConfig={{
          chatBaseUrl: "https://your-api.com/api",
          crudBaseUrl: "https://your-crud-api.com/api",
          headers: { "X-AIFABRIC-API-KEY": "your-key" },
        }}
      />
    </>
  );
}
```


---

## 4. Configuration Reference

### Full Configuration Object

Every property below is optional except `apiConfig.chatBaseUrl`.

```typescript
MaxMode.init({
  // ── REQUIRED ──────────────────────────────────────────────
  apiConfig: {
    chatBaseUrl: string,        // Chat / orchestration API base URL
    crudBaseUrl: string,        // CRUD API base URL (cart, conversations)
    headers: {                  // Headers sent with every request
      "X-AIFABRIC-API-KEY": "your-key",
      "Authorization": "Bearer ...",   // optional
    },
  },

  // ── IDENTITY ──────────────────────────────────────────────
  userId: "user_123",           // Scopes cart & conversations to this user.
                                // Default: auto-generated random ID.

  // ── FEATURES ──────────────────────────────────────────────
  features: {
    cart: true,                 // Show shopping cart panel
    debug: false,               // Show debug inspector (API req/res)
    conversations: true,        // Show conversation history sidebar
    quickActions: true,         // Show quick action buttons
  },

  // ── APPEARANCE ────────────────────────────────────────────
  theme: {
    primaryColor: "#6366f1",    // Brand color (hex). Applied to buttons,
                                // links, focus rings, launcher bubble.
    borderRadius: "0.5rem",     // Global border radius
    fontFamily: "Inter, sans-serif",
    darkMode: false,            // true | false | "auto"
  },
  position: "bottom-right",    // "bottom-right" | "bottom-left"
  launcher: true,               // Set false to hide floating button
                                // (control open/close yourself)

  // ── CALLBACKS ─────────────────────────────────────────────
  onEvent: (event) => { },      // Fires on every widget event
  onClose: () => { },           // Fires when widget is closed
});
```

### `apiConfig` (Required)

| Property | Type | Required | Description |
|----------|------|----------|-------------|
| `chatBaseUrl` | `string` | Yes | Base URL for chat orchestration API. All chat queries, suggestions, and conversation endpoints are relative to this URL. |
| `crudBaseUrl` | `string` | Yes | Base URL for CRUD operations API. Cart add/remove/get endpoints are relative to this URL. |
| `headers` | `Record<string, string>` | No | Additional headers sent with every API request. Use for API keys, auth tokens, etc. |

### `features`

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `cart` | `boolean` | `true` | Enable shopping cart panel and cart-related quick actions. |
| `debug` | `boolean` | `false` | Enable debug inspector panel showing raw API requests/responses. Useful during development. |
| `conversations` | `boolean` | `true` | Enable conversation history. Users can view, load, and delete past conversations. |
| `quickActions` | `boolean` | `true` | Show quick action buttons (Search Products, My Cart, Track Order, etc.). |

### `theme`

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `primaryColor` | `string` | `#6366f1` (indigo) | Brand color in hex format. Applied to the launcher button, primary buttons, focus rings, and accent elements. |
| `borderRadius` | `string` | `"0.5rem"` | Global border radius for cards, buttons, and inputs. |
| `fontFamily` | `string` | `"Inter, system-ui, sans-serif"` | CSS font-family stack. |
| `darkMode` | `boolean \| "auto"` | `false` | `true` forces dark mode. `"auto"` follows the user's system preference via `prefers-color-scheme`. |

---

## 5. Integration Guides

### Plain HTML / Static Sites

```html
<!-- Place before </body> -->
<script src="max-mode-widget.iife.js"></script>
<script>
  MaxMode.init({
    apiConfig: {
      chatBaseUrl: "https://your-api.com/api",
      crudBaseUrl: "https://your-crud-api.com/api",
      headers: { "X-AIFABRIC-API-KEY": "your-key" },
    },
    userId: "visitor-" + Date.now(),
    theme: { primaryColor: "#10b981" },
  });
</script>
```

### WordPress

Add to your theme's `footer.php` or use a plugin like "Insert Headers and Footers":

```html
<!-- Max Mode AI Assistant -->
<script src="<?php echo get_template_directory_uri(); ?>/assets/js/max-mode-widget.iife.js"></script>
<script>
  MaxMode.init({
    apiConfig: {
      chatBaseUrl: "https://your-api.com/api",
      crudBaseUrl: "https://your-crud-api.com/api",
      headers: { "X-AIFABRIC-API-KEY": "<?php echo get_option('maxmode_api_key'); ?>" },
    },
    userId: "<?php echo is_user_logged_in() ? wp_get_current_user()->ID : 'guest'; ?>",
  });
</script>
```

### Wix (Custom Code)

In the Wix Editor, go to **Settings > Custom Code** and add a code snippet with placement "Body - end":

```html
<script src="https://mahmoudashraf.github.io/aifabric/max-mode-widget.iife.js"></script>
<script>
  MaxMode.init({
    apiConfig: {
      chatBaseUrl: "https://your-api.com/api",
      crudBaseUrl: "https://your-crud-api.com/api",
    },
  });
</script>
```

### Next.js (App Router)

```tsx
// app/layout.tsx
import { MaxModeWidget } from "@anthropic/max-mode-widget";
import "@anthropic/max-mode-widget/styles.css";

// Client component wrapper needed for state
"use client";
import { useState } from "react";

function AIAssistant() {
  const [open, setOpen] = useState(false);

  return (
    <MaxModeWidget
      isOpen={open}
      onClose={() => setOpen(false)}
      apiConfig={{
        chatBaseUrl: process.env.NEXT_PUBLIC_CHAT_API_URL!,
        crudBaseUrl: process.env.NEXT_PUBLIC_CRUD_API_URL!,
        headers: { "X-AIFABRIC-API-KEY": process.env.NEXT_PUBLIC_API_KEY! },
      }}
      userId={userId}
      theme={{ primaryColor: "#000000" }}
    />
  );
}
```

### Vite + React

```tsx
// main.tsx
import { MaxModeWidget, useMaxMode } from "@anthropic/max-mode-widget";
import "@anthropic/max-mode-widget/styles.css";

function App() {
  const maxMode = useMaxMode();

  return (
    <div>
      <nav>
        <button onClick={maxMode.toggle}>AI Assistant</button>
      </nav>
      <MaxModeWidget
        isOpen={maxMode.isOpen}
        onClose={maxMode.close}
        apiConfig={{
          chatBaseUrl: import.meta.env.VITE_CHAT_API_URL,
          crudBaseUrl: import.meta.env.VITE_CRUD_API_URL,
        }}
      />
    </div>
  );
}
```


---

## 6. React API Reference

### `<MaxModeWidget />`

The main widget component. Renders the full AI assistant UI.

```tsx
import { MaxModeWidget } from "@anthropic/max-mode-widget";

<MaxModeWidget
  isOpen={boolean}              // Required. Controls visibility.
  onClose={() => void}          // Required. Called when user clicks close.
  apiConfig={MaxModeApiConfig}  // Required. API endpoints.
  userId={string}               // Optional. User identifier.
  initialAttachments={Array}    // Optional. Pre-attached items.
  features={MaxModeFeatures}    // Optional. Feature toggles.
  theme={MaxModeThemeConfig}    // Optional. Visual customization.
  onEvent={(event) => void}     // Optional. Event callback.
/>
```

**Props:**

| Prop | Type | Required | Description |
|------|------|----------|-------------|
| `isOpen` | `boolean` | Yes | Whether the widget is visible. |
| `onClose` | `() => void` | Yes | Callback when the user closes the widget. |
| `apiConfig` | `MaxModeApiConfig` | Yes | API configuration (see Section 4). |
| `userId` | `string` | No | User ID for scoping cart/conversations. |
| `initialAttachments` | `SharedAttachment[]` | No | Products/docs to pre-attach on first open. |
| `features` | `MaxModeFeatures` | No | Feature toggles (see Section 4). |
| `theme` | `MaxModeThemeConfig` | No | Theme overrides (see Section 4). |
| `onEvent` | `(event: MaxModeEvent) => void` | No | Fires on widget events (see Section 9). |

### `useMaxMode()` Hook

Provides programmatic control over the widget state.

```tsx
import { useMaxMode } from "@anthropic/max-mode-widget";

function MyComponent() {
  const {
    isOpen,              // boolean - current open state
    open,                // () => void - open the widget
    close,               // () => void - close the widget
    toggle,              // () => void - toggle open/closed
    attachProduct,       // (product) => void - attach a product
    pendingAttachments,  // SharedAttachment[] - queued items
    clearAttachments,    // () => void - clear the queue
  } = useMaxMode();

  return (
    <button onClick={toggle}>
      {isOpen ? "Close" : "Open"} AI Assistant
    </button>
  );
}
```

**`attachProduct(product)`**

Pre-attach a product to the chat. When the widget opens, the product will appear in the composer's attachments row:

```tsx
maxMode.attachProduct({
  sku: "WH-1000XM5",
  name: "Sony WH-1000XM5",
  price: 349.99,
  description: "Premium noise-canceling headphones",
  // ... any additional fields
});
```

### `<MaxModeProvider />`

Context provider for advanced use cases where you need multiple widgets or want to share state across components:

```tsx
import { MaxModeProvider } from "@anthropic/max-mode-widget";

function App() {
  return (
    <MaxModeProvider>
      <YourApp />
      {/* MaxModeWidget rendered deeper in the tree */}
    </MaxModeProvider>
  );
}
```

### TypeScript Exports

All types are exported for consumers:

```tsx
import type {
  MaxModeWidgetConfig,
  MaxModeApiConfig,
  MaxModeFeatures,
  MaxModeThemeConfig,
  MaxModeEvent,
  MaxModeEventType,
  Product,
  ChatMessage,
  Document,
  Conversation,
  ResultType,
  ChatResult,
  SanitizedPayload,
  SmartSuggestion,
  SharedAttachment,
} from "@anthropic/max-mode-widget";
```

---

## 7. Script Tag API Reference

When loaded via `<script>` tag, the widget exposes `window.MaxMode`:

### `MaxMode.init(config)`

Initialize and mount the widget. Must be called once.

```js
MaxMode.init({
  apiConfig: {
    chatBaseUrl: "https://your-api.com/api",
    crudBaseUrl: "https://your-crud-api.com/api",
    headers: { "X-AIFABRIC-API-KEY": "your-key" },
  },
  userId: "user_123",
  theme: { primaryColor: "#6366f1" },
  position: "bottom-right",
  launcher: true,
  features: { cart: true, debug: false },
  onEvent: function(event) { console.log(event); },
});
```

### `MaxMode.open()`

Open the widget programmatically:

```js
document.getElementById("help-btn").addEventListener("click", function() {
  MaxMode.open();
});
```

### `MaxMode.close()`

Close the widget:

```js
MaxMode.close();
```

### `MaxMode.toggle()`

Toggle the widget open or closed:

```js
MaxMode.toggle();
```

### `MaxMode.attachProduct(product)`

Attach a product to the chat context. The product will appear in the composer's attachments when the widget is opened:

```js
MaxMode.attachProduct({
  sku: "MBA-M3-256",
  name: "MacBook Air M3",
  price: 1099,
  description: "Apple MacBook Air with M3 chip",
  category: "Laptops",
  // Any additional fields are preserved and sent to the AI
});
```

**Common use case:** Attach the product the user is currently viewing:

```js
// On a product detail page
var productData = {
  sku: document.querySelector("[data-sku]").dataset.sku,
  name: document.querySelector("h1.product-title").textContent,
  price: parseFloat(document.querySelector(".price").textContent.replace("$", "")),
};
MaxMode.attachProduct(productData);
```

### `MaxMode.destroy()`

Completely unmount and remove the widget from the page:

```js
MaxMode.destroy();
// Widget is fully removed. Call MaxMode.init() to re-mount.
```

### `MaxMode.version`

```js
console.log(MaxMode.version); // "1.0.0"
```


---

## 8. Theming & Customization

### Brand Color

The `primaryColor` is the single most impactful theme setting. It controls the launcher bubble, primary buttons, focus rings, and link colors:

```js
MaxMode.init({
  apiConfig: { /* ... */ },
  theme: {
    primaryColor: "#10b981",  // Emerald green
  },
});
```

Common brand colors:

| Brand Style | Hex | Preview |
|-------------|-----|---------|
| Default (Indigo) | `#6366f1` | Primary buttons, launcher |
| Blue | `#3b82f6` | Professional / corporate |
| Green | `#10b981` | Eco / health brands |
| Orange | `#f97316` | Energetic / retail |
| Rose | `#f43f5e` | Fashion / beauty |
| Black | `#171717` | Luxury / minimal |

### Dark Mode

```js
// Force dark mode
MaxMode.init({
  theme: { darkMode: true },
  // ...
});

// Follow system preference
MaxMode.init({
  theme: { darkMode: "auto" },
  // ...
});
```

### Typography

```js
MaxMode.init({
  theme: {
    fontFamily: "'Poppins', 'Helvetica Neue', sans-serif",
  },
  // ...
});
```

### Border Radius

```js
// Sharp corners
MaxMode.init({ theme: { borderRadius: "0" } });

// Rounded (default)
MaxMode.init({ theme: { borderRadius: "0.5rem" } });

// Very rounded
MaxMode.init({ theme: { borderRadius: "1rem" } });
```

### Launcher Position

```js
// Bottom-right (default)
MaxMode.init({ position: "bottom-right" });

// Bottom-left
MaxMode.init({ position: "bottom-left" });
```

### Hide the Launcher Button

Use `launcher: false` when you want to trigger the widget from your own button:

```html
<button id="my-chat-btn">Need Help?</button>

<script>
  MaxMode.init({
    apiConfig: { /* ... */ },
    launcher: false,  // No floating bubble
  });

  document.getElementById("my-chat-btn").onclick = function() {
    MaxMode.open();
  };
</script>
```

### CSS Custom Properties (Advanced)

For fine-grained control, override CSS custom properties on the widget container. All variables are prefixed with `--mxw-`:

```css
/* Target the widget root (when using React entry) */
.max-mode-widget-root {
  --mxw-primary: 221.2 83.2% 53.3%;
  --mxw-primary-foreground: 210 40% 98%;
  --mxw-background: 0 0% 100%;
  --mxw-foreground: 222.2 84% 4.9%;
  --mxw-muted: 210 40% 96.1%;
  --mxw-muted-foreground: 215.4 16.3% 46.9%;
  --mxw-border: 214.3 31.8% 91.4%;
  --mxw-radius: 0.5rem;
  --mxw-font-family: Inter, sans-serif;
}
```

> Note: Color values use HSL format **without** the `hsl()` wrapper (Tailwind convention).

---

## 9. Events & Analytics

### Event Types

The `onEvent` callback fires for every significant widget interaction:

| Event Type | Data | Description |
|------------|------|-------------|
| `widget:opened` | `null` | Widget was opened |
| `widget:closed` | `null` | Widget was closed |
| `message:sent` | `{ content: string }` | User sent a chat message |
| `message:received` | `{ content: string, resultType: string }` | AI responded |
| `cart:add` | `{ product: Product }` | Item added to cart |
| `cart:remove` | `{ product: Product }` | Item removed from cart |
| `cart:checkout` | `{ cart: CartData }` | Checkout initiated |
| `product:view` | `{ product: Product }` | Product details viewed |
| `error` | `{ message: string, code: string }` | An error occurred |

### Event Object Shape

```typescript
interface MaxModeEvent {
  type: MaxModeEventType;   // e.g. "cart:add"
  data?: any;               // Event-specific payload
  timestamp: string;        // ISO 8601 timestamp
}
```

### Google Analytics Integration

```js
MaxMode.init({
  onEvent: function(event) {
    // Send to GA4
    if (typeof gtag !== "undefined") {
      gtag("event", event.type.replace(":", "_"), {
        event_category: "MaxMode",
        event_label: JSON.stringify(event.data),
      });
    }
  },
});
```

### Segment / Mixpanel

```js
MaxMode.init({
  onEvent: function(event) {
    if (typeof analytics !== "undefined") {
      analytics.track("MaxMode " + event.type, event.data || {});
    }
  },
});
```

### Custom Logging

```js
MaxMode.init({
  onEvent: function(event) {
    fetch("/api/analytics", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(event),
    });
  },
});
```


---

## 10. Shopify Integration

### Quick Setup

1. **Upload the widget file**
   - Go to your Shopify admin > Online Store > Themes > Edit code
   - Under "Assets", upload `max-mode-widget.iife.js`

2. **Add the snippet**
   - Create a new snippet called `max-mode-widget.liquid`
   - Paste the contents from `examples/shopify/snippet.liquid`

3. **Include in your theme**
   - Open `layout/theme.liquid`
   - Add before `</body>`:
   ```liquid
   {% include 'max-mode-widget' %}
   ```

4. **Configure settings**
   - Add these settings to `config/settings_schema.json`:
   ```json
   {
     "name": "AI Shopping Assistant",
     "settings": [
       { "type": "checkbox", "id": "maxmode_enabled", "label": "Enable AI Assistant", "default": true },
       { "type": "text", "id": "maxmode_api_key", "label": "API Key" },
       { "type": "text", "id": "maxmode_chat_url", "label": "Chat API URL" },
       { "type": "text", "id": "maxmode_crud_url", "label": "CRUD API URL" },
       { "type": "color", "id": "maxmode_primary_color", "label": "Widget Color", "default": "#6366f1" }
     ]
   }
   ```

### Auto Product Detection

On product pages, the Shopify snippet automatically:

1. Detects the current product using Shopify's `{{ product }}` Liquid object
2. Extracts SKU, title, price, description, images, and variant ID
3. Calls `MaxMode.attachProduct()` so the AI already knows what the customer is looking at

### Cart Sync

When the AI adds an item to its internal cart, the `onEvent` callback syncs with Shopify's AJAX Cart API:

```js
onEvent: function(event) {
  if (event.type === "cart:add" && event.data) {
    fetch("/cart/add.js", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        items: [{ id: event.data.shopifyVariantId, quantity: 1 }]
      })
    }).then(function() {
      // Trigger your theme's cart refresh
      document.dispatchEvent(new CustomEvent("cart:refresh"));
    });
  }
}
```

### Guest vs. Logged-In Users

```liquid
userId: {{ customer.id | default: 'guest' | json }},
features: {
  conversations: {{ customer | json }} !== null,  // Only show history for logged-in users
},
```

---

## 11. Architecture Deep Dive

### Package Structure

```
packages/max-mode-widget/
├── src/
│   ├── entries/                 # Build entry points
│   │   ├── iife.ts             # Script-tag entry -> window.MaxMode
│   │   ├── react.ts            # npm entry -> named exports
│   │   ├── MaxModeWidget.tsx   # React wrapper component
│   │   └── WidgetShell.tsx     # IIFE root (launcher + overlay)
│   │
│   ├── mount.ts                # Shadow DOM creation & React root
│   ├── config.ts               # Runtime config singleton
│   ├── context.ts              # Self-contained React context
│   ├── theme.ts                # CSS custom property engine
│   ├── constants.ts            # Quick actions, search categories
│   ├── types.ts                # TypeScript interfaces
│   │
│   ├── api/                    # API client layer
│   │   ├── client.ts           # Fetch wrappers (reads config at runtime)
│   │   ├── chat.ts             # POST /chat/query, /chat/suggestions
│   │   ├── conversations.ts    # GET/DELETE /chat/conversations
│   │   └── cart.ts             # POST/GET/DELETE /carts/active
│   │
│   ├── hooks/                  # React hooks (state management)
│   │   ├── useMaxModeController.ts   # Main orchestrator (composes all sub-hooks)
│   │   ├── useChatFlow.ts            # Chat query + response handling
│   │   ├── useCartController.ts      # Cart CRUD state
│   │   ├── useConversationsController.ts
│   │   ├── useAttachmentsController.ts
│   │   ├── useConfirmationFlow.ts
│   │   ├── useClarificationFlow.ts
│   │   ├── useSuggestionsController.ts
│   │   ├── useSearchControls.ts
│   │   ├── useMaxModePersistence.ts  # sessionStorage sync
│   │   ├── useMaxModeViewSync.ts     # Scroll, focus, observers
│   │   ├── useNewDocsPreviewActions.ts
│   │   ├── useMaxMode.ts            # Public hook for React consumers
│   │   └── use-toast.ts             # Toast notification state
│   │
│   ├── components/             # UI components (46 files)
│   │   ├── MaxModePage.tsx           # Root page component
│   │   ├── MaxModeView.tsx           # Layout wrapper
│   │   ├── MaxModeHeader.tsx
│   │   ├── Chat/                     # Chat UI
│   │   │   ├── MessageList.tsx
│   │   │   ├── MessageBubble.tsx     # Message rendering
│   │   │   ├── Composer.tsx          # Input area
│   │   │   ├── AIThinkingAnimation.tsx
│   │   │   └── Composer/            # Composer sub-components
│   │   ├── DesktopContextPanel/      # Right panel (desktop)
│   │   ├── MobileContextSheet/       # Bottom sheet (mobile)
│   │   ├── Conversations/
│   │   ├── Panels/DebugInspector/    # Debug UI (13 files)
│   │   └── ...
│   │
│   ├── ui/                     # Internalized UI primitives
│   │   ├── button.tsx          # (shadcn/ui, prefixed with mxw-)
│   │   ├── badge.tsx
│   │   ├── card.tsx
│   │   ├── textarea.tsx
│   │   └── toast-container.tsx
│   │
│   ├── integrations/
│   │   └── shopify.ts          # Shopify product detection + Cart API
│   │
│   ├── lib/utils.ts            # cn() utility (clsx + tailwind-merge)
│   └── styles/index.css        # Tailwind base + CSS custom properties
│
├── dist/                       # Build outputs
│   ├── max-mode-widget.esm.js  # ES module
│   ├── max-mode-widget.cjs.js  # CommonJS
│   ├── max-mode-widget.iife.js # Self-contained (React bundled)
│   ├── style.css               # For ESM/CJS consumers
│   └── *.d.ts                  # TypeScript declarations
│
├── examples/
│   ├── html/index.html         # Plain HTML demo
│   ├── react/App.tsx           # React demo
│   └── shopify/snippet.liquid  # Shopify Liquid snippet
│
├── vite.config.ts              # ESM/CJS build config
├── vite.config.iife.ts         # IIFE build config
├── tailwind.config.ts          # Widget Tailwind (mxw- prefix)
├── tsconfig.json
└── package.json
```

### How Isolation Works

**IIFE build (script tag):**
- Creates a Shadow DOM container attached to `document.body`
- All widget CSS is injected inside the Shadow DOM
- Host page styles cannot affect the widget; widget styles cannot leak out
- React + ReactDOM are bundled inside the IIFE (host page doesn't need React)

**ESM build (React apps):**
- Renders inside a regular `<div class="max-mode-widget-root">`
- All Tailwind classes use the `mxw-` prefix to avoid collisions
- CSS custom properties scoped to `.max-mode-widget-root`
- React is a peer dependency (uses the host app's React)

### State Management

The widget uses React Context + hooks (no Redux/Zustand):

```
useMaxModeController (orchestrator)
├── useChatFlow           — chat query/response cycle
├── useCartController     — cart CRUD
├── useConversationsController — history
├── useAttachmentsController — item attachments
├── useConfirmationFlow   — confirm/reject actions
├── useClarificationFlow  — clarification dialogs
├── useSuggestionsController — AI suggestions
├── useSearchControls     — search categories
├── useMaxModePersistence — sessionStorage sync
├── useMaxModeViewSync    — scroll, focus, observers
└── useNewDocsPreviewActions — mobile doc previews
```

### API Communication

All API calls go through `api/client.ts` which reads the runtime config:

```
User types query
  → useChatFlow builds payload (query + attachments + position/mode)
  → api/chat.ts calls POST /chat/query
  → Response parsed: result type, documents, suggestions
  → State updated: messages, documents, position, mode
  → UI re-renders
```


---

## 12. Development Guide

### Prerequisites

- Node.js 18+
- npm 10+

### Setup

```bash
cd packages/max-mode-widget
npm install
```

### Commands

| Command | Description |
|---------|-------------|
| `npm run dev` | Watch mode (rebuilds on file changes) |
| `npm run build` | Production build (ESM + CJS + IIFE) |
| `npm run build:esm` | Build ESM/CJS only |
| `npm run build:iife` | Build IIFE only |
| `npm run typecheck` | TypeScript validation (no emit) |

### Adding a New Feature

1. **Add the feature toggle** in `src/config.ts` under `MaxModeFeatures`
2. **Implement the UI** in the relevant component
3. **Gate it** behind the feature flag:
   ```tsx
   const config = getWidgetConfig();
   if (config.features?.myNewFeature) {
     // render feature
   }
   ```
4. **Export the type** in `src/entries/react.ts` if needed

### Adding a New Hook

1. Create `src/hooks/useMyHook.ts`
2. Wire it into `useMaxModeController.ts` (the orchestrator)
3. Pass needed values through the controller return object

### Adding a New API Endpoint

1. Add the function in the appropriate file under `src/api/`
2. Always use `apiFetchJson()` or `apiFetchResponse()` from `client.ts` — these automatically read the base URL and headers from the runtime config
3. Never hardcode URLs

### Testing Locally

Open `examples/html/index.html` in a browser after building:

```bash
npm run build
open examples/html/index.html
```

Or serve it:

```bash
npx serve dist
```

---

## 13. Troubleshooting

### Widget doesn't appear

- **Check the console** for `[MaxMode]` error messages.
- Verify `apiConfig.chatBaseUrl` is set and the URL is reachable.
- If using the IIFE build, make sure the script loaded (check Network tab).

### CSS conflicts with host site

- **IIFE users:** The widget uses Shadow DOM — CSS should be fully isolated. If you see leaks, check if a global `* { all: unset }` or similar rule is applied.
- **React users:** All widget classes use the `mxw-` prefix. If there are conflicts, ensure you're importing the widget CSS *after* your own styles.

### API errors (CORS)

If you see CORS errors in the console:
- Ensure your API server includes `Access-Control-Allow-Origin` for the widget's host domain.
- Check that the `headers` in `apiConfig` don't trigger a preflight request without proper server CORS support.

### Widget opens but shows blank / loading forever

- Check that `chatBaseUrl` points to a running API Fabric instance.
- Open the Debug Inspector (`features: { debug: true }`) to see raw request/response data.
- Verify the API key is valid.

### SessionStorage errors

The widget persists conversation state in `sessionStorage`. If the browser blocks storage (private browsing, iframe with `allow-storage` missing), the widget still works but won't persist across page reloads.

### Multiple widgets on one page

Currently only one widget instance is supported per page. Calling `MaxMode.init()` twice will log a warning. Call `MaxMode.destroy()` first to re-initialize.

---

## 14. FAQ

**Q: Does the widget work without React on my site?**

Yes. The IIFE build bundles React internally. Your site doesn't need React, or any framework at all. Just add the `<script>` tag.

**Q: How big is the widget?**

~192 KB gzipped for the IIFE (includes React). ~144 KB gzipped for the ESM build (React is external).

**Q: Can I use it in an iframe?**

Yes, but ensure the iframe's `allow` attribute includes `storage` if you want conversation persistence.

**Q: Can I customize the launcher button?**

Set `launcher: false` and create your own button that calls `MaxMode.open()`.

**Q: Is the widget accessible (WCAG)?**

The widget uses semantic HTML, ARIA labels, and keyboard navigation. Focus management is handled for dialogs and the chat input.

**Q: Can I use multiple themes per page?**

Not currently. Theme is set once at `init()` time and applies globally.

**Q: How does conversation persistence work?**

Conversations are stored in `sessionStorage` (cleared when the browser tab closes). The conversation ID is also sent to the API so server-side history can be retrieved.

**Q: Can the widget work offline?**

No. The widget requires an active connection to the Chat and CRUD APIs.

**Q: What browsers are supported?**

All modern browsers (Chrome 80+, Firefox 78+, Safari 14+, Edge 80+). Shadow DOM is required for the IIFE build. IE11 is not supported.

---

## Appendix: API Endpoints Used

The widget calls the following endpoints on your backend:

### Chat API (`chatBaseUrl`)

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/chat/query` | Send a user message and get AI response |
| `POST` | `/chat/suggestions` | Get AI-powered suggestions for attached items |
| `GET` | `/chat/conversations?ownerId=...` | List user's conversations |
| `GET` | `/chat/conversations/:id?ownerId=...` | Get conversation detail with turns |
| `DELETE` | `/chat/conversations/:id?ownerId=...` | Delete a conversation |

### CRUD API (`crudBaseUrl`)

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/carts/active?userId=...` | Get user's active cart |
| `POST` | `/carts/active/items` | Add item to cart (`{ userId, sku, quantity }`) |
| `DELETE` | `/carts/active/items?userId=...&sku=...` | Remove item from cart |

---

*This guide is for Max Mode Widget v1.0.0.*
