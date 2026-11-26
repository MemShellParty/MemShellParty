import type { RemoteSelectUiHint, ShellToolConfigValue } from "@/types/memshell";

import { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";

import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";

import { fetchRemoteOptions, type RemoteOption } from "./remote-options";

type LoadStatus = "loading" | "ready" | "error";

export function RemoteSelect({
  tool,
  field,
  hint,
  value,
  onChange,
  autoSelectFirst = false,
}: Readonly<{
  tool: string;
  field: string;
  hint: RemoteSelectUiHint;
  value: ShellToolConfigValue | undefined;
  onChange: (value: ShellToolConfigValue | undefined) => void;
  autoSelectFirst?: boolean;
}>) {
  const { t } = useTranslation(["memshell", "common"]);
  const [options, setOptions] = useState<RemoteOption[]>([]);
  const [status, setStatus] = useState<LoadStatus>("loading");
  const [retryNonce, setRetryNonce] = useState(0);
  const onChangeRef = useRef(onChange);
  onChangeRef.current = onChange;

  useEffect(() => {
    onChangeRef.current(undefined);
    setOptions([]);
    const controller = new AbortController();
    setStatus("loading");
    void fetchRemoteOptions(tool, field, controller.signal)
      .then((nextOptions) => {
        if (controller.signal.aborted) return;
        setOptions(nextOptions);
        if (autoSelectFirst) onChangeRef.current(nextOptions[0]?.value);
        setStatus("ready");
      })
      .catch(() => {
        if (controller.signal.aborted) return;
        setStatus("error");
      });
    return () => controller.abort();
  }, [tool, field, retryNonce, autoSelectFirst]);

  const selectedValue =
    typeof value === "string" && options.some((option) => option.value === value) ? value : "";
  const unavailable = status !== "ready" || options.length === 0;

  return (
    <div className="space-y-1.5">
      <Select
        disabled={unavailable}
        items={options}
        onValueChange={(nextValue: string | null) => {
          if (nextValue !== null) onChange(nextValue);
        }}
        value={selectedValue}
      >
        <SelectTrigger aria-required={hint.required}>
          <SelectValue
            data-placeholder={
              status === "loading" ? t("common:loading") : t("common:placeholders.select")
            }
          />
        </SelectTrigger>
        <SelectContent>
          {options.map((option) => (
            <SelectItem key={option.value} value={option.value}>
              {option.label}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
      {status === "error" ? (
        <div className="flex items-center gap-2 text-xs text-destructive" role="alert">
          <span>{t("memshell:remoteOptions.loadFailed")}</span>
          <button
            className="underline underline-offset-2"
            onClick={() => setRetryNonce((current) => current + 1)}
            type="button"
          >
            {t("common:configLoadError.retry")}
          </button>
        </div>
      ) : null}
      {status === "ready" && options.length === 0 ? (
        <p className="text-xs text-muted-foreground">{t("memshell:remoteOptions.empty")}</p>
      ) : null}
    </div>
  );
}
