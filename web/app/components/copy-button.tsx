import type { VariantProps } from "class-variance-authority";

import { Check, Copy } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import CopyToClipboard from "react-copy-to-clipboard";
import { useTranslation } from "react-i18next";
import { toast } from "sonner";

import { Button, type buttonVariants } from "@/components/ui/button";
import { cn } from "@/lib/utils";

interface CopyButtonProps extends React.ComponentProps<"button"> {
  value: string;
  src?: string;
  /** 复制成功后触发（用于「已使用」自动标记等场景） */
  onCopy?: () => void;
}

export function CopyButton({
  value,
  className,
  onCopy,
  ...buttonProps
}: Readonly<CopyButtonProps & VariantProps<typeof buttonVariants>>) {
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
      onCopy?.();
    }
  }, [hasCopied, onCopy, t]);

  return (
    <CopyToClipboard.CopyToClipboard text={value} onCopy={handleCopy}>
      <Button
        {...buttonProps}
        variant="ghost"
        size="icon"
        type="button"
        className={cn("h-7 w-7 [&_svg]:h-4 [&_svg]:w-4", className)}
        disabled={hasCopied || buttonProps.disabled}
      >
        {hasCopied ? <Check /> : <Copy />}
      </Button>
    </CopyToClipboard.CopyToClipboard>
  );
}
