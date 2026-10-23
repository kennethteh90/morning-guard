const CHALLENGE_PREFIX = "challenge:";
const CHALLENGE_TTL_SEC = 120;

function randomNonce(): string {
  const bytes = new Uint8Array(32);
  crypto.getRandomValues(bytes);
  return btoa(String.fromCharCode(...bytes))
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/g, "");
}

export async function issueChallenge(kv: KVNamespace): Promise<{ nonce: string }> {
  const nonce = randomNonce();
  await kv.put(`${CHALLENGE_PREFIX}${nonce}`, "1", { expirationTtl: CHALLENGE_TTL_SEC });
  return { nonce };
}

/** Returns true when the nonce was present and is now consumed. */
export async function consumeChallenge(kv: KVNamespace, nonce: string): Promise<boolean> {
  const key = `${CHALLENGE_PREFIX}${nonce.trim()}`;
  if (!nonce.trim()) return false;
  const existing = await kv.get(key);
  if (!existing) return false;
  await kv.delete(key);
  return true;
}
