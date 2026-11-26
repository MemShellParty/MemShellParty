import { env } from "@/config";
import { fetchJson } from "@/utils/fetch-json";

export interface RemoteOption {
  label: string;
  value: string;
}

interface RemoteOptionsResponse {
  options: Array<RemoteOption>;
}

export function normalizeRemoteOptions(payload: unknown): RemoteOption[] {
  if (typeof payload !== "object" || payload === null) {
    throw new Error("Remote options response must be an object");
  }
  const options = (payload as Partial<RemoteOptionsResponse>).options;
  if (!Array.isArray(options)) {
    throw new Error("Remote options response must contain an options array");
  }
  return options.map((option) => {
    if (
      typeof option !== "object" ||
      option === null ||
      typeof option.label !== "string" ||
      typeof option.value !== "string" ||
      !option.value.trim()
    ) {
      throw new Error("Remote options must contain string labels and non-blank values");
    }
    return { label: option.label, value: option.value };
  });
}

export async function fetchRemoteOptions(
  tool: string,
  field: string,
  signal: AbortSignal,
): Promise<RemoteOption[]> {
  const url =
    `${env.API_URL}/api/config/shelltool/${encodeURIComponent(tool)}` +
    `/fields/${encodeURIComponent(field)}/options`;
  const payload = await fetchJson<unknown>(url, {
    method: "GET",
    cache: "no-store",
    signal,
  });
  return normalizeRemoteOptions(payload);
}
