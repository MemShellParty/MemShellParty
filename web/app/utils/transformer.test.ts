import type { ShellToolConfigEntry } from "@/types/memshell";

import assert from "node:assert/strict";
import { test } from "node:test";

import { type MemShellFormSchema, memShellFormSchema } from "@/types/schema";
import {
  generateShareableUrl,
  missingRequiredRemoteFields,
  restoreMemShellFormValues,
  sanitizeMemShellHistoryValues,
  transformToPostData,
} from "@/utils/transformer";

function memShellValues(overrides: Record<string, unknown> = {}): MemShellFormSchema {
  return {
    server: "Tomcat",
    serverVersion: "Unknown",
    shellTool: "Godzilla",
    shellType: "Listener",
    packingMethod: "DefaultBase64",
    ...overrides,
  };
}

test("shareable URLs retain Godzilla even when it is not the configured first tool", () => {
  const previousWindow = Object.getOwnPropertyDescriptor(globalThis, "window");
  Object.defineProperty(globalThis, "window", {
    configurable: true,
    value: { location: { origin: "https://example.test", pathname: "/memshell" } },
  });
  try {
    const url = new URL(generateShareableUrl(memShellValues({ shellTool: "Godzilla" })));
    assert.equal(url.searchParams.get("shellTool"), "Godzilla");
  } finally {
    if (previousWindow) Object.defineProperty(globalThis, "window", previousWindow);
    else Reflect.deleteProperty(globalThis, "window");
  }
});

test("memshell transformation defaults a missing obfuscation scope to ALL", () => {
  const { shellConfig } = transformToPostData(memShellValues());

  assert.equal(shellConfig.obfuscationScope, "ALL");
  assert.deepEqual(shellConfig.obfuscation, { enabled: false });
});

test("memshell transformation keeps the selected scope beside obfuscation", () => {
  const { shellConfig } = transformToPostData(
    memShellValues({ obfuscation: true, obfuscationScope: "INJECTOR" }),
  );

  assert.equal(shellConfig.obfuscationScope, "INJECTOR");
  assert.deepEqual(shellConfig.obfuscation, { enabled: true, options: {} });
});

test("disabling obfuscation does not enable it through scope selection", () => {
  const { shellConfig } = transformToPostData(
    memShellValues({ obfuscation: false, obfuscationScope: "MEMSHELL" }),
  );

  assert.equal(shellConfig.obfuscationScope, "MEMSHELL");
  assert.deepEqual(shellConfig.obfuscation, { enabled: false });
});

test("history restore defaults only a missing scope and preserves existing values", () => {
  const historical = memShellValues({ shellClassName: "example.Shell" });
  const restored = restoreMemShellFormValues(historical);

  assert.equal(restored.obfuscationScope, "ALL");
  assert.equal(restored.shellClassName, "example.Shell");
  assert.equal(historical.obfuscationScope, undefined);

  assert.equal(
    restoreMemShellFormValues(memShellValues({ obfuscationScope: "MEMSHELL" })).obfuscationScope,
    "MEMSHELL",
  );
});

function remoteConfig(required = true): ShellToolConfigEntry[] {
  return [
    {
      tool: "Godzilla",
      params: [
        {
          name: "profile",
          type: "object",
          options: [],
          group: "BASIC",
          ui: {
            kind: "remote-select",
            urlQueryParam: "profileUrlParam",
            itemsPath: "",
            labelPath: "name",
            valuePath: "id",
            required,
          },
        },
      ],
    },
    {
      tool: "Behinder",
      params: [{ name: "unrelated", type: "string", options: [], group: "BASIC" }],
    },
  ];
}

test("memshell transformation preserves a selected object and filters other tools", () => {
  const profile = { id: "a", name: "Profile A", nested: { enabled: true } };
  const { shellToolConfig } = transformToPostData(
    memShellValues({ profile, unrelated: "must-not-leak" }),
    remoteConfig(),
  );

  assert.equal(shellToolConfig.profile, profile);
  assert.equal(shellToolConfig.unrelated, undefined);
});

test("the form schema retains dynamic remote object values", async () => {
  const profile = { id: "a", name: "Profile A" };
  const validated = await memShellFormSchema.validate(memShellValues({ profile }));
  assert.deepEqual((validated as Record<string, unknown>).profile, profile);
});

test("memshell transformation rejects non-object values for object fields", () => {
  assert.throws(
    () => transformToPostData(memShellValues({ profile: ["not", "an", "object"] }), remoteConfig()),
    /must be a JSON object: profile/,
  );
  assert.throws(
    () => transformToPostData(memShellValues({ profile: "{}" }), remoteConfig()),
    /must be a JSON object: profile/,
  );
});

test("required remote fields fail validation only when no value is selected", () => {
  assert.deepEqual(missingRequiredRemoteFields(memShellValues(), remoteConfig()), ["profile"]);
  assert.deepEqual(
    missingRequiredRemoteFields(memShellValues({ profile: {} }), remoteConfig()),
    [],
  );
  assert.deepEqual(missingRequiredRemoteFields(memShellValues(), remoteConfig(false)), []);
});

test("history sanitizing removes remote values without mutating ordinary form state", () => {
  const values = memShellValues({ profile: { id: "a" }, pass: "ordinary-value" });
  const sanitized = sanitizeMemShellHistoryValues(values, remoteConfig());

  assert.equal((sanitized as Record<string, unknown>).profile, undefined);
  assert.equal(sanitized.pass, "ordinary-value");
  assert.deepEqual((values as Record<string, unknown>).profile, { id: "a" });
});

test("history sanitizing also removes remote values left on an inactive tool", () => {
  const inactiveRemote = remoteConfig()[1];
  const configs: ShellToolConfigEntry[] = [
    { tool: "Godzilla", params: [] },
    { ...inactiveRemote, params: [remoteConfig()[0].params[0]] },
  ];
  const sanitized = sanitizeMemShellHistoryValues(
    memShellValues({ profile: { id: "inactive" } }),
    configs,
  );
  assert.equal((sanitized as Record<string, unknown>).profile, undefined);
});

test("history sanitizing preserves a same-named ordinary field on the current tool", () => {
  const ordinary: ShellToolConfigEntry = {
    tool: "Godzilla",
    params: [{ name: "profile", type: "string", options: [], group: "BASIC" }],
  };
  const inactiveRemote = remoteConfig()[1];
  const configs = [ordinary, { ...inactiveRemote, params: [remoteConfig()[0].params[0]] }];
  const sanitized = sanitizeMemShellHistoryValues(
    memShellValues({ profile: "ordinary-current-value" }),
    configs,
  );
  assert.equal((sanitized as Record<string, unknown>).profile, "ordinary-current-value");
});
