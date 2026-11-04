import type { RemoteSelectUiHint } from "@/types/memshell";

import assert from "node:assert/strict";
import { test } from "node:test";

import {
  fetchRemoteOptions,
  normalizeRemoteOptions,
  remoteOptionKeyForValue,
  validateRemoteOptionsUrl,
} from "@/components/memshell/fields/remote-options";

function hint(overrides: Partial<RemoteSelectUiHint> = {}): RemoteSelectUiHint {
  return {
    kind: "remote-select",
    urlQueryParam: "profileUrlParam",
    itemsPath: "",
    labelPath: "name",
    valuePath: "id",
    required: true,
    ...overrides,
  };
}

test("object mode preserves the complete item when labels are duplicated", () => {
  const first = { id: "a", name: "same", config: { level: 1 } };
  const second = { id: "b", name: "same", config: { level: 2 } };
  const options = normalizeRemoteOptions([first, second], hint(), true);

  assert.deepEqual(
    options.map((option) => option.label),
    ["same", "same"],
  );
  assert.equal(options[0].value, first);
  assert.equal(options[1].value, second);
  assert.equal(remoteOptionKeyForValue(options, second), "1");
});

test("nested items and numeric IDs support string mode", () => {
  const options = normalizeRemoteOptions(
    { data: { items: [{ id: 7, name: "seven", ignored: true }] } },
    hint({ itemsPath: "data.items" }),
    false,
  );
  assert.deepEqual(options, [{ key: "0", label: "seven", value: "7" }]);
});

test("malformed option responses fail closed", () => {
  assert.throws(() => normalizeRemoteOptions({ items: [] }, hint(), true), /itemsPath/);
  assert.throws(() => normalizeRemoteOptions(["not-an-object"], hint(), true), /must be objects/);
  assert.throws(() => normalizeRemoteOptions([{ id: "a" }], hint(), true), /labels/);
  assert.throws(
    () => normalizeRemoteOptions([{ id: "a", name: "a" }], hint({ valuePath: "" }), false),
    /require valuePath/,
  );
});

test("HTTP and HTTPS URLs are accepted while other schemes are rejected", () => {
  assert.equal(
    validateRemoteOptionsUrl("http://localhost:8080/options?token=a"),
    "http://localhost:8080/options?token=a",
  );
  assert.equal(
    validateRemoteOptionsUrl("https://example.test/options"),
    "https://example.test/options",
  );
  assert.throws(() => validateRemoteOptionsUrl("javascript:alert(1)"), /HTTP or HTTPS/);
  assert.throws(() => validateRemoteOptionsUrl("data:application/json,[]"), /HTTP or HTTPS/);
});

test("remote loading uses a direct GET without ambient credentials", async (t) => {
  const originalFetch = globalThis.fetch;
  t.after(() => {
    globalThis.fetch = originalFetch;
  });
  let requestUrl = "";
  let requestInit: RequestInit | undefined;
  globalThis.fetch = async (input, init) => {
    requestUrl = String(input);
    requestInit = init;
    return new Response(JSON.stringify([{ id: "a", name: "Profile A" }]), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });
  };

  const options = await fetchRemoteOptions(
    "http://localhost/options?token=secret",
    hint(),
    true,
    new AbortController().signal,
  );
  assert.equal(requestUrl, "http://localhost/options?token=secret");
  assert.equal(requestInit?.method, "GET");
  assert.equal(requestInit?.credentials, "omit");
  assert.equal(requestInit?.cache, "no-store");
  assert.equal(requestInit?.referrerPolicy, "no-referrer");
  assert.deepEqual(options[0].value, { id: "a", name: "Profile A" });
});
