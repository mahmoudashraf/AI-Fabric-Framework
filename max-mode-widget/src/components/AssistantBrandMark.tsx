import { useState } from "react";
import { BrainCircuit, type LucideIcon } from "lucide-react";

function safeLogoUrl(value?: string): string | undefined {
  if (
    typeof value !== "string" || value.length > 2048 || value !== value.trim() || value.includes("#")
    || !/^https:\/\/[^/?#@]+(?:[/?#]|$)/i.test(value) || /[\u0000-\u0020\\\u007f]/.test(value)
  ) {
    return undefined;
  }
  try {
    const url = new URL(value);
    if (url.protocol === "https:" && url.hostname && !url.username && !url.password) {
      return value;
    }
  } catch {
    // Invalid host-supplied URLs use the same mark as an unavailable image.
  }
  return undefined;
}

export function AssistantBrandMark({
  logoUrl,
  fallbackIcon: FallbackIcon = BrainCircuit,
  className = "h-5 w-5",
}: {
  logoUrl?: string;
  fallbackIcon?: LucideIcon;
  className?: string;
}) {
  const [failedUrl, setFailedUrl] = useState<string>();
  const source = safeLogoUrl(logoUrl);

  return (
    <span aria-hidden="true" className={`inline-flex shrink-0 items-center justify-center overflow-hidden ${className}`}>
      {source && source !== failedUrl ? (
        <img
          key={source}
          src={source}
          alt=""
          className="h-full w-full object-contain"
          referrerPolicy="no-referrer"
          decoding="async"
          onError={() => setFailedUrl(source)}
        />
      ) : (
        <FallbackIcon className="h-full w-full" />
      )}
    </span>
  );
}
