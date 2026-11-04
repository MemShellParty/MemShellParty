import type { RemoteSelectUiHint, ShellToolConfigParam } from "@/types/memshell";

export function isRemoteParam(
  param: ShellToolConfigParam,
): param is ShellToolConfigParam & { ui: RemoteSelectUiHint } {
  return param.ui?.kind === "remote-select";
}

export function isRandomizableParam(param: ShellToolConfigParam): boolean {
  return !isRemoteParam(param) && (param.type === "string" || param.type === "enum");
}

export function displayedShellToolParams(
  params: ShellToolConfigParam[],
  hideRandomizable: boolean,
): ShellToolConfigParam[] {
  return hideRandomizable ? params.filter(isRemoteParam) : params;
}
