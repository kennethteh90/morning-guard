/** Best-effort KV write; logs and swallows errors so callers can still succeed. */
export async function kvPutBestEffort(
  kv: KVNamespace,
  key: string,
  value: string,
  options?: KVNamespacePutOptions,
): Promise<boolean> {
  try {
    await kv.put(key, value, options);
    return true;
  } catch (e) {
    console.warn("kv_put_failed", {
      key: key.slice(0, 80),
      message: e instanceof Error ? e.message : String(e).slice(0, 200),
    });
    return false;
  }
}