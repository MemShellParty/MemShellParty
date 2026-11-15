import type { VariantProps } from "class-variance-authority";

import { Check, Copy } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import CopyToClipboard from "react-copy-to-clipboard";
import { useTranslation } from "react-i18next";
import { toast } from "sonner";

import { Button, type buttonVariants } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { cn } from "@/lib/utils";

export interface CopyOption {
  label: string;
  value: string;
  disabled?: boolean;
  /** 该选项复制成功后触发 */
  onCopy?: () => void;
}

export function CopyMenuButton({
  options,
  className,
  ...buttonProps
}: Readonly<
  { options: CopyOption[] } & React.ComponentProps<"button"> & VariantProps<typeof buttonVariants>
>) {
  const [hasCopied, setHasCopied] = useState(false);
  const { t } = useTranslation(["common"]);

  useEffect(() => {
    if (hasCopied) {
      const timer = setTimeout(() => {
        setHasCopied(false);
      }, 1000);
      return () => clearTimeout(timer);
    }
  }, [hasCopied]);

  const handleCopy = useCallback(() => {
    if (!hasCopied) {
      setHasCopied(true);
      toast.success(t("copySuccess"), { duration: 1000 });
    }
  }, [hasCopied, t]);

  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        render={
          <Button
            {...buttonProps}
            variant="ghost"
            size="icon"
            type="button"
            className={cn("h-7 w-7 [&_svg]:h-4 [&_svg]:w-4", className)}
          />
        }
      >
        {hasCopied ? <Check /> : <Copy />}
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        {options.map((option) => (
          <CopyToClipboard.CopyToClipboard
            key={option.label}
            text={option.value}
            onCopy={() => {
              handleCopy();
              option.onCopy?.();
            }}
          >
            <DropdownMenuItem disabled={option.disabled}>{option.label}</DropdownMenuItem>
          </CopyToClipboard.CopyToClipboard>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
