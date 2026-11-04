import type { ShellToolConfigParam } from "@/types/memshell";

import assert from "node:assert/strict";
import { test } from "node:test";

import { displayedShellToolParams, isRandomizableParam, isRemoteParam } from "./shelltool-params";

const regular: ShellToolConfigParam = {
  name: "pass",
  type: "string",
  options: [],
  group: "BASIC",
};
const remote: ShellToolConfigParam = {
  name: "profile",
  type: "object",
  options: [],
  group: "BASIC",
  ui: {
    kind: "remote-select",
    urlQueryParam: "profileUrlParam",
    itemsPath: "",
    labelPath: "name",
    required: true,
  },
};

test("random parameter mode hides regular fields but keeps remote fields visible", () => {
  assert.deepEqual(displayedShellToolParams([regular, remote], true), [remote]);
  assert.deepEqual(displayedShellToolParams([regular, remote], false), [regular, remote]);
});

test("remote-only fields do not participate in random parameter handling", () => {
  assert.equal(isRemoteParam(remote), true);
  assert.equal(isRemoteParam(regular), false);
  assert.deepEqual(
    [remote].filter((param) => !isRemoteParam(param)),
    [],
  );
  assert.equal(isRandomizableParam(regular), true);
  assert.equal(isRandomizableParam(remote), false);
  assert.equal(isRandomizableParam({ ...regular, type: "object" }), false);
});
