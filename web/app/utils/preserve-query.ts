export type QueryLocation = Pick<Location, "pathname" | "search">;

const SERVER_ORIGIN = "http://preserve-query.local";

export function preserveQuery(url: string, location: QueryLocation): string {
  if (!location.search || location.search === "?") return url;

  const origin = typeof window === "undefined" ? SERVER_ORIGIN : window.location.origin;
  let target: URL;
  try {
    target = new URL(url, `${origin}${location.pathname}`);
  } catch {
    return url;
  }
  if (target.origin !== origin) return url;

  const current = new URLSearchParams(location.search);
  const targetNames = new Set(target.searchParams.keys());
  for (const [name, value] of current) {
    if (!targetNames.has(name)) target.searchParams.append(name, value);
  }
  return `${target.pathname}${target.search}${target.hash}`;
}

export function preserveCurrentQuery(url: string): string {
  if (typeof window === "undefined") return url;
  return preserveQuery(url, window.location);
}
