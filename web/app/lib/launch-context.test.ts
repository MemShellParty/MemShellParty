import assert from "node:assert/strict";
import { test } from "node:test";

import {
  createLaunchContext,
  type LaunchContextEnvironment,
  type LaunchContextStorage,
} from "@/lib/launch-context";

function environmentHarness() {
  const query = new Map<string, string>();
  const stored = new Map<string, string>();
  let clearCount = 0;
  const storage: LaunchContextStorage = {
    getItem: (key) => stored.get(key) ?? null,
    setItem: (key, value) => stored.set(key, value),
  };
  const environment: LaunchContextEnvironment = {
    getQueryParam: (name) => query.get(name) ?? null,
    clearQueryParam(name) {
      clearCount += 1;
      query.delete(name);
    },
    getStorage: () => storage,
  };
  return { query, stored, environment, clearCount: () => clearCount };
}

test("launch context stores a query parameter for later routes and refreshes", () => {
  const harness = environmentHarness();
  const context = createLaunchContext(harness.environment);
  harness.query.set("profileUrlParam", "http://localhost:8080/options?user=a");

  assert.equal(context.get("profileUrlParam"), "http://localhost:8080/options?user=a");
  assert.equal(harness.query.has("profileUrlParam"), false);
  assert.equal(harness.clearCount(), 1);
  assert.deepEqual([...harness.stored.values()], ["http://localhost:8080/options?user=a"]);

  // The query is gone after navigation, while sessionStorage still supplies the same URL.
  assert.equal(context.get("profileUrlParam"), "http://localhost:8080/options?user=a");
});

test("a new query parameter overrides the old launch identity and notifies subscribers", () => {
  const harness = environmentHarness();
  const context = createLaunchContext(harness.environment);
  harness.query.set("profileUrlParam", "https://example.test/options?user=a");
  context.get("profileUrlParam");

  let notifications = 0;
  const unsubscribe = context.subscribe("profileUrlParam", () => {
    notifications += 1;
  });
  harness.query.set("profileUrlParam", "https://example.test/options?user=b");
  assert.equal(context.get("profileUrlParam"), "https://example.test/options?user=b");
  assert.equal(notifications, 1);
  assert.deepEqual([...harness.stored.values()], ["https://example.test/options?user=b"]);

  unsubscribe();
});

test("storage failure retains the query and still provides an in-memory fallback", () => {
  const harness = environmentHarness();
  harness.environment.getStorage = () => ({
    getItem: () => null,
    setItem: () => {
      throw new Error("storage unavailable");
    },
  });
  harness.environment.clearQueryParam = () => {
    throw new Error("query must be retained when storage fails");
  };
  const context = createLaunchContext(harness.environment);
  harness.query.set("profileUrlParam", "http://internal.test/options");

  assert.equal(context.get("profileUrlParam"), "http://internal.test/options");
  assert.equal(harness.query.get("profileUrlParam"), "http://internal.test/options");

  // Even if route handling later removes the query, this application instance retains the value.
  harness.query.delete("profileUrlParam");
  assert.equal(context.get("profileUrlParam"), "http://internal.test/options");
});

test("missing launch parameters remain absent", () => {
  const harness = environmentHarness();
  const context = createLaunchContext(harness.environment);
  assert.equal(context.get("profileUrlParam"), undefined);
});
