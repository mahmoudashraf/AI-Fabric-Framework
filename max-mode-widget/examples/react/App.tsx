/**
 * Example: Using Max Mode Widget in a React application.
 *
 * Install:
 *   npm install @anthropic/max-mode-widget
 *
 * Then import:
 *   import { MaxModeWidget, useMaxMode } from '@anthropic/max-mode-widget';
 *   import '@anthropic/max-mode-widget/styles.css';
 */

import React from "react";
// In a real app: import { MaxModeWidget, useMaxMode } from '@anthropic/max-mode-widget';
// import '@anthropic/max-mode-widget/styles.css';
import { MaxModeWidget } from "../../src/entries/react";
import { useMaxMode } from "../../src/hooks/useMaxMode";

export default function App() {
  const { isOpen, open, close, toggle, attachProduct } = useMaxMode();

  return (
    <div style={{ fontFamily: "system-ui", padding: "2rem" }}>
      <h1>React + Max Mode Widget</h1>

      <div style={{ display: "flex", gap: "0.5rem", margin: "1rem 0" }}>
        <button onClick={open}>Open Widget</button>
        <button onClick={close}>Close Widget</button>
        <button onClick={toggle}>Toggle</button>
      </div>

      <div style={{ display: "flex", gap: "0.5rem", margin: "1rem 0" }}>
        <button
          onClick={() =>
            attachProduct({
              sku: "WH-1000XM5",
              name: "Sony WH-1000XM5",
              price: 349.99,
            })
          }
        >
          Attach Sony Headphones
        </button>
      </div>

      {/* The Widget */}
      <MaxModeWidget
        isOpen={isOpen}
        onClose={close}
        apiConfig={{
          chatBaseUrl:
            "https://rest-connector-dep-1bf14c33-dev.up.railway.app/api",
          crudBaseUrl:
            "https://ai-fabric-framework-production-a247.up.railway.app/api",
          headers: { "X-AIFABRIC-API-KEY": "test" },
        }}
        userId="demo-user-react"
        features={{
          cart: true,
          debug: false,
          quickActions: true,
          conversations: true,
        }}
        theme={{
          primaryColor: "#6366f1",
          borderRadius: "0.75rem",
        }}
        onEvent={(event) => console.log("[MaxMode Event]", event)}
      />
    </div>
  );
}
