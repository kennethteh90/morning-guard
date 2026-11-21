import assert from "node:assert/strict";
import { afterEach, beforeEach, describe, it, mock } from "node:test";
import worker from "../src/index";
import { clearLicensedAuthTestHooks } from "../src/licensed-auth";
import { baseEnv } from "./helpers";

describe("graceful degradation", () => {
  let originalFetch: typeof fetch;

  beforeEach(() => {
    originalFetch = globalThis.fetch;
    clearLicensedAuthTestHooks();
  });

  afterEach(() => {
    globalThis.fetch = originalFetch;
    mock.restoreAll();
    clearLicensedAuthTestHooks();
  });

  it("returns 200 on chapter miss even when edge cache put is unavailable", async () => {
    globalThis.fetch = mock.fn(async () =>
      new Response(
        JSON.stringify({ reference: "John 3", text: "16. For God so loved the world" }),
        { status: 200, headers: { "Content-Type": "application/json" } },
      ),
    ) as typeof fetch;

    const request = new Request(
      "https://example.com/v1/chapter?translation=web&book=John&chapter=3",
      { headers: { "CF-Connecting-IP": "203.0.113.55" } },
    );
    const response = await worker.fetch(request, baseEnv());
    assert.equal(response.status, 200);
    assert.equal(response.headers.get("X-Cache"), "MISS");
  });
});