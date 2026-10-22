/** Constant-time string compare (same length only). */
export function tokensMatch(expected: string, provided: string): boolean {
  if (expected.length !== provided.length) {
    return false;
  }
  let diff = 0;
  for (let i = 0; i < expected.length; i++) {
    diff |= expected.charCodeAt(i) ^ provided.charCodeAt(i);
  }
  return diff === 0;
}
