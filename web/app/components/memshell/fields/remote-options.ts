import type {
  RemoteSelectUiHint,
  ShellToolConfigObject,
  ShellToolConfigValue,
} from "@/types/memshell";

import { fetchJson } from "@/utils/fetch-json";

export interface RemoteOption {
  key: string;
  label: string;
  value: ShellToolConfigValue;
}

function isObject(value: unknown): value is ShellToolConfigObject {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

/** Deliberately supports only dot-separated property access, never executable expressions. */
export function readRemotePath(value: unknown, path: string): unknown {
  if (!path) return value;
  let current = value;
  for (const segment of path.split(".")) {
    if (!segment || !isObject(current) || !(segment in current)) return undefined;
    current = current[segment];
  }
  return current;
}

export function normalizeRemoteOptions(
  payload: unknown,
  hint: RemoteSelectUiHint,
  objectMode: boolean,
): RemoteOption[] {
  const items = readRemotePath(payload, hint.itemsPath);
  if (!Array.isArray(items)) {
    throw new Error("Remote options response does not contain an array at itemsPath");
  }
  return items.map((item, index) => {
    if (!isObject(item)) throw new Error("Remote option items must be objects");
    const rawLabel = readRemotePath(item, hint.labelPath);
    if (typeof rawLabel !== "string" && typeof rawLabel !== "number") {
      throw new Error("Remote option labels must be strings or numbers");
    }

    let value: ShellToolConfigValue = item;
    if (!objectMode) {
      if (!hint.valuePath) throw new Error("String remote options require valuePath");
      const rawValue = readRemotePath(item, hint.valuePath);
      if (typeof rawValue !== "string" && typeof rawValue !== "number") {
        throw new Error("Remote option values must be strings or numbers");
      }
      value = String(rawValue);
    }
    return { key: String(index), label: String(rawLabel), value };
  });
}

export function validateRemoteOptionsUrl(value: string): string {
  const url = new URL(value);
  if (url.protocol !== "http:" && url.protocol !== "https:") {
    throw new Error("Remote options URL must use HTTP or HTTPS");
  }
  return url.toString();
}

export async function fetchRemoteOptions(
  sourceUrl: string,
  hint: RemoteSelectUiHint,
  objectMode: boolean,
  signal: AbortSignal,
): Promise<RemoteOption[]> {
  const url = validateRemoteOptionsUrl(sourceUrl);
  const payload = await fetchJson<unknown>(url, {
    method: "GET",
    credentials: "omit",
    cache: "no-store",
    referrerPolicy: "no-referrer",
    signal,
  });
  return normalizeRemoteOptions(payload, hint, objectMode);
}

export function remoteOptionKeyForValue(
  options: RemoteOption[],
  value: ShellToolConfigValue | undefined,
): string {
  if (value === undefined) return "";
  if (typeof value === "string") {
    return options.find((option) => option.value === value)?.key ?? "";
  }
  const direct = options.find((option) => option.value === value);
  if (direct) return direct.key;
  const serialized = JSON.stringify(value);
  return (
    options.find(
      (option) => typeof option.value === "object" && JSON.stringify(option.value) === serialized,
    )?.key ?? ""
  );
}
