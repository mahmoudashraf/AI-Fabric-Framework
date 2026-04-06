import type { Config } from "tailwindcss";

export default {
  // Prefix all classes to avoid collisions with host site
  prefix: "mxw-",
  darkMode: ["class"],
  content: ["./src/**/*.{ts,tsx}"],
  theme: {
    container: {
      center: true,
      padding: "2rem",
      screens: {
        "2xl": "1400px",
      },
    },
    extend: {
      fontFamily: {
        sans: ["var(--mxw-font-family, Inter, system-ui, sans-serif)"],
        mono: ["JetBrains Mono", "monospace"],
      },
      colors: {
        border: "hsl(var(--mxw-border, 214.3 31.8% 91.4%))",
        input: "hsl(var(--mxw-input, 214.3 31.8% 91.4%))",
        ring: "hsl(var(--mxw-ring, 221.2 83.2% 53.3%))",
        background: "hsl(var(--mxw-background, 0 0% 100%))",
        foreground: "hsl(var(--mxw-foreground, 222.2 84% 4.9%))",
        primary: {
          DEFAULT: "hsl(var(--mxw-primary, 221.2 83.2% 53.3%))",
          foreground: "hsl(var(--mxw-primary-foreground, 210 40% 98%))",
        },
        secondary: {
          DEFAULT: "hsl(var(--mxw-secondary, 210 40% 96.1%))",
          foreground: "hsl(var(--mxw-secondary-foreground, 222.2 47.4% 11.2%))",
        },
        destructive: {
          DEFAULT: "hsl(var(--mxw-destructive, 0 84.2% 60.2%))",
          foreground: "hsl(var(--mxw-destructive-foreground, 210 40% 98%))",
        },
        muted: {
          DEFAULT: "hsl(var(--mxw-muted, 210 40% 96.1%))",
          foreground: "hsl(var(--mxw-muted-foreground, 215.4 16.3% 46.9%))",
        },
        accent: {
          DEFAULT: "hsl(var(--mxw-accent, 210 40% 96.1%))",
          foreground: "hsl(var(--mxw-accent-foreground, 222.2 47.4% 11.2%))",
        },
        popover: {
          DEFAULT: "hsl(var(--mxw-popover, 0 0% 100%))",
          foreground: "hsl(var(--mxw-popover-foreground, 222.2 84% 4.9%))",
        },
        card: {
          DEFAULT: "hsl(var(--mxw-card, 0 0% 100%))",
          foreground: "hsl(var(--mxw-card-foreground, 222.2 84% 4.9%))",
        },
      },
      borderRadius: {
        lg: "var(--mxw-radius, 0.5rem)",
        md: "calc(var(--mxw-radius, 0.5rem) - 2px)",
        sm: "calc(var(--mxw-radius, 0.5rem) - 4px)",
      },
      keyframes: {
        "fade-in": {
          from: { opacity: "0", transform: "translateY(10px)" },
          to: { opacity: "1", transform: "translateY(0)" },
        },
        "slide-in-left": {
          from: { opacity: "0", transform: "translateX(-20px)" },
          to: { opacity: "1", transform: "translateX(0)" },
        },
        "slide-in-right": {
          from: { opacity: "0", transform: "translateX(20px)" },
          to: { opacity: "1", transform: "translateX(0)" },
        },
        "scale-in": {
          from: { opacity: "0", transform: "scale(0.95)" },
          to: { opacity: "1", transform: "scale(1)" },
        },
      },
      animation: {
        "fade-in": "fade-in 0.5s ease-out forwards",
        "slide-in-left": "slide-in-left 0.5s ease-out forwards",
        "slide-in-right": "slide-in-right 0.5s ease-out forwards",
        "scale-in": "scale-in 0.3s ease-out forwards",
      },
    },
  },
  plugins: [],
} satisfies Config;
