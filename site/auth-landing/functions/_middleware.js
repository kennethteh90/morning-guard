/**
 * HTTP Basic Auth for /demo/* (iOS interactive mockup).
 * Set Pages secrets: DEMO_BASIC_AUTH_USER, DEMO_BASIC_AUTH_PASSWORD
 */
function parseBasicAuth(header) {
  if (!header || !header.startsWith("Basic ")) return null;
  try {
    const decoded = atob(header.slice(6));
    const colon = decoded.indexOf(":");
    if (colon < 0) return null;
    return { user: decoded.slice(0, colon), pass: decoded.slice(colon + 1) };
  } catch {
    return null;
  }
}

function unauthorized() {
  return new Response("Authentication required", {
    status: 401,
    headers: {
      "WWW-Authenticate": 'Basic realm="Morning Guard Demo", charset="UTF-8"',
      "Content-Type": "text/plain; charset=utf-8",
    },
  });
}

export async function onRequest(context) {
  const url = new URL(context.request.url);
  if (!url.pathname.startsWith("/demo")) {
    return context.next();
  }

  const expectedUser = context.env.DEMO_BASIC_AUTH_USER;
  const expectedPass = context.env.DEMO_BASIC_AUTH_PASSWORD;
  if (!expectedUser || !expectedPass) {
    return new Response("Demo is not configured (missing DEMO_BASIC_AUTH_* secrets).", {
      status: 503,
      headers: { "Content-Type": "text/plain; charset=utf-8" },
    });
  }

  const creds = parseBasicAuth(context.request.headers.get("Authorization"));
  if (!creds || creds.user !== expectedUser || creds.pass !== expectedPass) {
    return unauthorized();
  }

  return context.next();
}
