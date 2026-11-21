import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { consumeChallenge, issueChallenge } from "../src/challenge";
import { baseEnv } from "./helpers";

describe("challenge", () => {
  it("issues stateless nonces when session HMAC secret is configured", async () => {
    const env = baseEnv({
      BIBLE_PROXY_SESSION_HMAC_SECRET: "test-session-hmac-secret-value",
    });
    const { nonce } = await issueChallenge(env);
    assert.match(nonce, /^mgc1\./);
    assert.equal(await consumeChallenge(env, nonce), true);
    assert.equal(await consumeChallenge(env, nonce), false);
  });

  it("accepts legacy KV nonces when no HMAC secret is configured", async () => {
    const env = baseEnv();
    const { nonce } = await issueChallenge(env);
    assert.doesNotMatch(nonce, /^mgc1\./);
    assert.equal(await consumeChallenge(env, nonce), true);
    assert.equal(await consumeChallenge(env, nonce), false);
  });

  it("rejects tampered stateless nonces", async () => {
    const env = baseEnv({
      BIBLE_PROXY_SESSION_HMAC_SECRET: "test-session-hmac-secret-value",
    });
    const { nonce } = await issueChallenge(env);
    const tampered = `${nonce}x`;
    assert.equal(await consumeChallenge(env, tampered), false);
    assert.equal(await consumeChallenge(env, nonce), true);
  });

  it("fails consume when used-marker KV put fails", async () => {
    const env = baseEnv({
      BIBLE_PROXY_SESSION_HMAC_SECRET: "test-session-hmac-secret-value",
      BIBLE_CACHE: {
        get: async () => null,
        put: async () => {
          throw new Error("kv full");
        },
        delete: async () => {},
        list: async () => ({ keys: [], list_complete: true, cacheStatus: null }),
        getWithMetadata: async () => ({ value: null, metadata: null, cacheStatus: null }),
      } as unknown as KVNamespace,
    });
    const { nonce } = await issueChallenge(env);
    assert.equal(await consumeChallenge(env, nonce), false);
  });
});