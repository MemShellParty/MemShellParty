const STORAGE_PREFIX = "memshell-party:launch-context:v1:";

export interface LaunchContextStorage {
  getItem(key: string): string | null;
  setItem(key: string, value: string): void;
}

/**
 * Browser dependencies are injected so launch-parameter lifecycle can be tested without a DOM.
 * Access is lazy: importing this module during SSR must not touch window or storage.
 */
export interface LaunchContextEnvironment {
  getQueryParam(name: string): string | null;
  clearQueryParam(name: string): void;
  getStorage(): LaunchContextStorage | null;
}

const browserEnvironment: LaunchContextEnvironment = {
  getQueryParam(name) {
    if (typeof window === "undefined") return null;
    return new URLSearchParams(window.location.search).get(name);
  },
  clearQueryParam(name) {
    if (typeof window === "undefined") return;
    const url = new URL(window.location.href);
    url.searchParams.delete(name);
    window.history.replaceState(window.history.state, "", url);
  },
  getStorage() {
    if (typeof window === "undefined") return null;
    return window.sessionStorage;
  },
};

export interface LaunchContext {
  get(name: string): string | undefined;
  subscribe(name: string, listener: () => void): () => void;
}

/**
 * Keeps sensitive launch parameters available across in-app routes and refreshes in this browser
 * tab. Query parameters win over stored values; a new launch therefore replaces an older identity.
 */
export function createLaunchContext(
  environment: LaunchContextEnvironment = browserEnvironment,
): LaunchContext {
  const memory = new Map<string, string>();
  const listeners = new Map<string, Set<() => void>>();

  const notify = (name: string) => {
    for (const listener of listeners.get(name) ?? []) listener();
  };

  return {
    get(name) {
      const fromQuery = environment.getQueryParam(name);
      if (fromQuery) {
        const changed = memory.get(name) !== fromQuery;
        memory.set(name, fromQuery);
        let stored = false;
        try {
          const storage = environment.getStorage();
          if (storage) {
            storage.setItem(`${STORAGE_PREFIX}${name}`, fromQuery);
            stored = true;
          }
        } catch {
          // Keep the query as a navigation fallback when persistent tab storage is unavailable.
        }
        if (stored) {
          try {
            environment.clearQueryParam(name);
          } catch {
            // A URL that cannot be rewritten remains a valid fallback on the next read.
          }
        }
        if (changed) notify(name);
        return fromQuery;
      }

      try {
        const stored = environment.getStorage()?.getItem(`${STORAGE_PREFIX}${name}`);
        if (stored) {
          memory.set(name, stored);
          return stored;
        }
      } catch {
        // Fall back to the in-memory value for this application lifetime.
      }
      return memory.get(name);
    },

    subscribe(name, listener) {
      const subscribers = listeners.get(name) ?? new Set<() => void>();
      subscribers.add(listener);
      listeners.set(name, subscribers);
      return () => {
        subscribers.delete(listener);
        if (subscribers.size === 0) listeners.delete(name);
      };
    },
  };
}

export const launchContext = createLaunchContext();
