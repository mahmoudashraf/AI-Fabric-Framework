import * as React from "react";
import { Slot } from "@radix-ui/react-slot";
import { cva, type VariantProps } from "class-variance-authority";

import { cn } from "@/lib/utils";

const buttonVariants = cva(
  "mxw-inline-flex mxw-items-center mxw-justify-center mxw-gap-2 mxw-whitespace-nowrap mxw-rounded-lg mxw-text-sm mxw-font-semibold mxw-ring-offset-background mxw-transition-all mxw-duration-200 focus-visible:mxw-outline-none focus-visible:mxw-ring-2 focus-visible:mxw-ring-ring focus-visible:mxw-ring-offset-2 disabled:mxw-pointer-events-none disabled:mxw-opacity-50 [&_svg]:mxw-pointer-events-none [&_svg]:mxw-size-4 [&_svg]:mxw-shrink-0",
  {
    variants: {
      variant: {
        default:
          "mxw-bg-primary mxw-text-primary-foreground hover:mxw-bg-primary/90 mxw-shadow-md hover:mxw-shadow-lg active:mxw-scale-[0.98]",
        destructive:
          "mxw-bg-destructive mxw-text-destructive-foreground hover:mxw-bg-destructive/90",
        outline:
          "mxw-border mxw-border-input mxw-bg-background hover:mxw-bg-accent hover:mxw-text-accent-foreground",
        secondary:
          "mxw-bg-secondary mxw-text-secondary-foreground hover:mxw-bg-secondary/80",
        ghost:
          "hover:mxw-bg-accent hover:mxw-text-accent-foreground",
        link: "mxw-text-primary mxw-underline-offset-4 hover:mxw-underline",
      },
      size: {
        default: "mxw-h-10 mxw-px-4 mxw-py-2",
        sm: "mxw-h-9 mxw-rounded-md mxw-px-3",
        lg: "mxw-h-12 mxw-px-6 mxw-text-base",
        xl: "mxw-h-14 mxw-px-8 mxw-text-lg",
        icon: "mxw-h-10 mxw-w-10",
      },
    },
    defaultVariants: {
      variant: "default",
      size: "default",
    },
  },
);

export interface ButtonProps
  extends React.ButtonHTMLAttributes<HTMLButtonElement>,
    VariantProps<typeof buttonVariants> {
  asChild?: boolean;
}

const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant, size, asChild = false, ...props }, ref) => {
    const Comp = asChild ? Slot : "button";
    return (
      <Comp
        className={cn(buttonVariants({ variant, size, className }))}
        ref={ref}
        {...props}
      />
    );
  },
);
Button.displayName = "Button";

export { Button, buttonVariants };
