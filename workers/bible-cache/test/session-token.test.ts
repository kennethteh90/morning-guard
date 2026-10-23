import assert from "node:assert/strict";
import { describe, it } from "node:test";
import {
  issueSessionToken,
  sessionTokensConfigured,
  verifySessionToken,
} from "../src/session-token";
import type { Env } from "../src/upstream";

function mockKv(): KVNamespace {
  return {
    get: async () => null,
    put: async () => {},
    delete: async () => {},
    list: async () => ({ keys: [], list_complete: true, cacheStatus: null }),
    getWithMetadata: async () => ({ value: null, metadata: null, cacheStatus: null }),
  } as unknown as KVNamespace;
}

function envWithSecret(secret: string | undefined): Env {
  return {
    BIBLE_CACHE: mockKv(),
    API_BIBLE_KEY: "k",
    BIBLE_PROXY_SESSION_HMAC_SECRET: secret,
  };
}

describe("session tokens", () => {
  it("requires a sufficiently long secret", () => {
    assert.equal(sessionTokensConfigured(envWithSecret(undefined)), false);
    assert.equal(sessionTokensConfigured(envWithSecret("short")), false);
    assert.equal(sessionTokensConfigured(envWithSecret("sixteen-chars-ok")), true);
  });

  it("issues and verifies a token bound to package and platform", async () => {
    const env = envWithSecret("test-session-hmac-secret-value");
    const now = 1_700_000_000;
    const issued = await issueSessionToken(
      env,
      { pkg: "com.morningguard.app", plat: "android" },
      now,
    );
    assert.ok(issued);
    assert.equal(issued!.expiresAt, now + 3600);
    const claims = await verifySessionToken(env, issued!.token, now + 10);
    assert.deepEqual(claims, {
      exp: now + 3600,
      pkg: "com.morningguard.app",
      plat: "android",
    });
  });

  it("rejects expired, tampered, or wrong-package tokens", async () => {
    const env = envWithSecret("test-session-hmac-secret-value");
    const now = 1_700_000_000;
    const issued = await issueSessionToken(
      env,
      { pkg: "com.morningguard.app", plat: "android" },
      now,
    );
    assert.ok(issued);

    assert.equal(await verifySessionToken(env, issued!.token, now + 3601), null);

    const tampered = issued!.token.replace(/\.[^.]+$/, ".AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
    assert.equal(await verifySessionToken(env, tampered, now + 10), null);

    const otherPkg = await issueSessionToken(
      env,
      { pkg: "com.evil.app", plat: "android", exp: now + 3600 },
      now,
    );
    assert.ok(otherPkg);
    assert.equal(await verifySessionToken(env, otherPkg!.token, now + 10), null);
  });

  it("fails closed when secret is missing", async () => {
    const env = envWithSecret("");
    assert.equal(
      await issueSessionToken(env, { pkg: "com.morningguard.app", plat: "android" }),
      null,
    );
    assert.equal(await verifySessionToken(env, "mg1.abc.def"), null);
  });
});
