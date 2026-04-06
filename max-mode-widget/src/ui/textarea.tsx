import * as React from "react";
import { cn } from "@/lib/utils";

export interface TextareaProps
  extends React.TextareaHTMLAttributes<HTMLTextAreaElement> {}

const Textarea = React.forwardRef<HTMLTextAreaElement, TextareaProps>(
  ({ className, ...props }, ref) => {
    return (
      <textarea
        className={cn(
          "mxw-flex mxw-min-h-[80px] mxw-w-full mxw-rounded-md mxw-border mxw-border-input mxw-bg-background mxw-px-3 mxw-py-2 mxw-text-sm mxw-ring-offset-background placeholder:mxw-text-muted-foreground focus-visible:mxw-outline-none focus-visible:mxw-ring-2 focus-visible:mxw-ring-ring focus-visible:mxw-ring-offset-2 disabled:mxw-cursor-not-allowed disabled:mxw-opacity-50",
          className,
        )}
        ref={ref}
        {...props}
      />
    );
  },
);
Textarea.displayName = "Textarea";

export { Textarea };
