import assert from "node:assert/strict";
import { test } from "node:test";

import {
  fetchRemoteOptions,
  normalizeRemoteOptions,
} from "@/components/memshell/fields/remote-options";

test("normalized options expose only labels and stable identifiers", () => {
  const options = normalizeRemoteOptions({
    options: [
      { label: "same", value: "a" },
      { label: "same", value: "b" },
    ],
  });

  assert.deepEqual(options, [
    { label: "same", value: "a" },
    { label: "same", value: "b" },
  ]);
});

test("malformed option responses fail closed", () => {
  assert.throws(() => normalizeRemoteOptions(null), /must be an object/);
  assert.throws(() => normalizeRemoteOptions([]), /options array/);
  assert.throws(() => normalizeRemoteOptions({}), /options array/);
  assert.throws(
    () => normalizeRemoteOptions({ options: [{ label: "a", value: "" }] }),
    /non-blank/,
  );
  assert.throws(() => normalizeRemoteOptions({ options: [{ label: "a" }] }), /string labels/);
});

test("remote loading calls the backend field endpoint without an external URL", async (t) => {
  const originalFetch = globalThis.fetch;
  t.after(() => {
    globalThis.fetch = originalFetch;
  });
  let requestUrl = "";
  let requestInit: RequestInit | undefined;
  globalThis.fetch = async (input, init) => {
    requestUrl = String(input);
    requestInit = init;
    return new Response(JSON.stringify({ options: [{ label: "Profile A", value: "profile-a" }] }), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });
  };

  const options = await fetchRemoteOptions(
    "Red Queen",
    "profile/key",
    new AbortController().signal,
  );
  assert.match(requestUrl, /\/api\/config\/shelltool\/Red%20Queen\/fields\/profile%2Fkey\/options/);
  assert.equal(requestInit?.method, "GET");
  assert.equal(requestInit?.cache, "no-store");
  assert.deepEqual(options, [{ label: "Profile A", value: "profile-a" }]);
});
