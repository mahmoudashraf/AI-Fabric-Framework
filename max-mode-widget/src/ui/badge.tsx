import * as React from "react";
import { cva, type VariantProps } from "class-variance-authority";
import { cn } from "@/lib/utils";

const badgeVariants = cva(
  "mxw-inline-flex mxw-items-center mxw-rounded-full mxw-border mxw-px-2.5 mxw-py-0.5 mxw-text-xs mxw-font-semibold mxw-transition-colors focus:mxw-outline-none focus:mxw-ring-2 focus:mxw-ring-ring focus:mxw-ring-offset-2",
  {
    variants: {
      variant: {
        default:
          "mxw-border-transparent mxw-bg-primary mxw-text-primary-foreground hover:mxw-bg-primary/80",
        secondary:
          "mxw-border-transparent mxw-bg-secondary mxw-text-secondary-foreground hover:mxw-bg-secondary/80",
        destructive:
          "mxw-border-transparent mxw-bg-destructive mxw-text-destructive-foreground hover:mxw-bg-destructive/80",
        outline: "mxw-text-foreground",
      },
    },
    defaultVariants: {
      variant: "default",
    },
  },
);

export interface BadgeProps
  extends React.HTMLAttributes<HTMLDivElement>,
    VariantProps<typeof badgeVariants> {}

function Badge({ className, variant, ...props }: BadgeProps) {
  return (
    <div className={cn(badgeVariants({ variant }), className)} {...props} />
  );
}

export { Badge, badgeVariants };
