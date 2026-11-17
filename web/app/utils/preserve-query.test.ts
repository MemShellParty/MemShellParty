import assert from "node:assert/strict";
import { test } from "node:test";

import { preserveQuery } from "@/utils/preserve-query";

const location = {
  pathname: "/ui/mcp",
  search: "?X-Platform-Credential=secret&lang=zh-CN",
};

test("internal navigation preserves the current query", () => {
  assert.equal(
    preserveQuery("/ui/docs/self-host", location),
    "/ui/docs/self-host?X-Platform-Credential=secret&lang=zh-CN",
  );
});

test("custom credential names and duplicate values are preserved", () => {
  assert.equal(
    preserveQuery("/ui/about", {
      pathname: "/ui/mcp",
      search: "?access_token=one&access_token=two",
    }),
    "/ui/about?access_token=one&access_token=two",
  );
});

test("target query parameters and hash are retained without duplicate credential names", () => {
  assert.equal(
    preserveQuery("/ui/docs/mcp?lang=en#clients", location),
    "/ui/docs/mcp?lang=en&X-Platform-Credential=secret#clients",
  );
  assert.equal(
    preserveQuery("/ui/docs/mcp?X-Platform-Credential=existing", location),
    "/ui/docs/mcp?X-Platform-Credential=existing&lang=zh-CN",
  );
});

test("external and non-HTTP links are unchanged", () => {
  assert.equal(preserveQuery("https://example.com/docs", location), "https://example.com/docs");
  assert.equal(preserveQuery("//example.com/docs", location), "//example.com/docs");
  assert.equal(
    preserveQuery("mailto:security@example.com", location),
    "mailto:security@example.com",
  );
});

test("relative links resolve against the current page", () => {
  assert.equal(
    preserveQuery("../about", location),
    "/about?X-Platform-Credential=secret&lang=zh-CN",
  );
});

test("an empty current query leaves URLs untouched", () => {
  assert.equal(preserveQuery("/ui/docs", { pathname: "/ui/mcp", search: "" }), "/ui/docs");
});
