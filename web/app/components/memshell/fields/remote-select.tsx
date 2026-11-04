import type { RemoteSelectUiHint, ShellToolConfigValue } from "@/types/memshell";

import { useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";

import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { launchContext } from "@/lib/launch-context";

import { fetchRemoteOptions, remoteOptionKeyForValue, type RemoteOption } from "./remote-options";

type LoadStatus = "loading" | "ready" | "missing-source" | "error";

export function RemoteSelect({
  hint,
  objectMode,
  value,
  onChange,
}: Readonly<{
  hint: RemoteSelectUiHint;
  objectMode: boolean;
  value: ShellToolConfigValue | undefined;
  onChange: (value: ShellToolConfigValue | undefined) => void;
}>) {
  const { t } = useTranslation(["memshell", "common"]);
  const [sourceUrl, setSourceUrl] = useState("");
  const [sourceResolved, setSourceResolved] = useState(false);
  const [options, setOptions] = useState<RemoteOption[]>([]);
  const [status, setStatus] = useState<LoadStatus>("loading");
  const [retryNonce, setRetryNonce] = useState(0);
  const onChangeRef = useRef(onChange);
  onChangeRef.current = onChange;

  useEffect(() => {
    let currentSource = "";
    setSourceResolved(false);
    const updateSource = () => {
      const nextSource = launchContext.get(hint.urlQueryParam) ?? "";
      if (nextSource !== currentSource) {
        currentSource = nextSource;
        setSourceUrl(nextSource);
      }
      setSourceResolved(true);
    };
    const unsubscribe = launchContext.subscribe(hint.urlQueryParam, updateSource);
    updateSource();
    return unsubscribe;
  }, [hint.urlQueryParam]);

  useEffect(() => {
    if (!sourceResolved) return;
    onChangeRef.current(undefined);
    setOptions([]);
    if (!sourceUrl) {
      setStatus("missing-source");
      return;
    }

    const controller = new AbortController();
    setStatus("loading");
    void fetchRemoteOptions(sourceUrl, hint, objectMode, controller.signal)
      .then((nextOptions) => {
        if (controller.signal.aborted) return;
        setOptions(nextOptions);
        setStatus("ready");
      })
      .catch(() => {
        if (controller.signal.aborted) return;
        setStatus("error");
      });
    return () => controller.abort();
  }, [sourceResolved, sourceUrl, retryNonce, hint, objectMode]);

  const selectItems = useMemo(
    () => options.map((option) => ({ value: option.key, label: option.label })),
    [options],
  );
  const selectedKey = remoteOptionKeyForValue(options, value);
  const unavailable = status !== "ready" || options.length === 0;

  return (
    <div className="space-y-1.5">
      <Select
        disabled={unavailable}
        items={selectItems}
        onValueChange={(nextKey: string | null) => {
          const selected = options.find((option) => option.key === nextKey);
          if (selected) onChange(selected.value);
        }}
        value={selectedKey}
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
            <SelectItem key={option.key} value={option.key}>
              {option.label}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
      {status === "missing-source" ? (
        <p className="text-xs text-destructive" role="alert">
          {t("memshell:remoteOptions.missingSource")}
        </p>
      ) : null}
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
