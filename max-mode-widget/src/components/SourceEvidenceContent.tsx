import { structuredSourceContent } from "@/sourcePresentation";
import { StructuredResultValue } from "./ActionResultRenderer";

export function SourceEvidenceContent({ content }: { content: string }) {
  const value = structuredSourceContent(content);
  return value ? <div className="min-w-0 text-sm"><StructuredResultValue value={value} /></div>
    : <p className="whitespace-pre-wrap break-words text-sm leading-relaxed text-muted-foreground">{content}</p>;
}
