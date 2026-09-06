const json = (data, status = 200, extra = {}) => new Response(JSON.stringify(data), {
  status,
  headers: { "content-type": "application/json; charset=utf-8", ...extra }
});

const cors = (origin, allowed) => {
  const ok = allowed === "*" || !origin || allowed.split(",").map(v => v.trim()).includes(origin);
  return {
    "access-control-allow-origin": ok ? (allowed === "*" ? "*" : origin || allowed) : "null",
    "access-control-allow-methods": "POST,OPTIONS",
    "access-control-allow-headers": "content-type, authorization, x-universal-client",
    "access-control-max-age": "86400",
    "vary": "Origin"
  };
};

const cleanText = value => String(value || "").replace(/\\u0000/g, " ").trim();

function validHttpUrl(value) {
  try {
    const u = new URL(value);
    return u.protocol === "https:" || u.protocol === "http:";
  } catch (_) { return false; }
}

async function fetchPageText(url) {
  if (!validHttpUrl(url)) throw new Error("Page URL must be HTTP(S)");
  const u = new URL(url);
  if (["localhost", "127.0.0.1", "0.0.0.0", "::1"].includes(u.hostname)) throw new Error("Local addresses are not allowed");
  const response = await fetch(url, {
    headers: { "user-agent": "Universal-AI-Gateway/1.0", "accept": "text/html,application/xhtml+xml,text/plain;q=0.8,*/*;q=0.1" },
    redirect: "follow"
  });
  if (!response.ok) throw new Error(`Page fetch failed: HTTP ${response.status}`);
  const type = response.headers.get("content-type") || "";
  if (!type.includes("text/html") && !type.includes("text/plain") && !type.includes("application/xhtml+xml")) {
    throw new Error("The page is not readable HTML/text");
  }
  const html = (await response.text()).slice(0, 2_000_000);
  return html
    .replace(/<script[\\s\\S]*?<\\/script>/gi, " ")
    .replace(/<style[\\s\\S]*?<\\/style>/gi, " ")
    .replace(/<noscript[\\s\\S]*?<\\/noscript>/gi, " ")
    .replace(/<[^>]+>/g, " ")
    .replace(/&nbsp;/gi, " ")
    .replace(/&amp;/gi, "&")
    .replace(/&lt;/gi, "<")
    .replace(/&gt;/gi, ">")
    .replace(/\\s+/g, " ")
    .trim()
    .slice(0, 120_000);
}

async function callProvider(env, prompt, context) {
  const base = cleanText(env.AI_BASE_URL).replace(/\\/$/, "");
  const model = cleanText(env.AI_MODEL);
  const key = cleanText(env.AI_API_KEY);
  if (!base || !model || !key) throw new Error("Universal AI gateway is not configured");
  const response = await fetch(`${base}/chat/completions`, {
    method: "POST",
    headers: { "content-type": "application/json", "authorization": `Bearer ${key}` },
    body: JSON.stringify({
      model,
      temperature: 0.2,
      messages: [
        { role: "system", content: "You are Universal AI, the secure assistant for Universal Browser. Answer from the supplied page context. Be accurate, concise and say when the context is insufficient." },
        { role: "user", content: `${prompt}\n\nPAGE CONTEXT:\n${context}` }
      ]
    })
  });
  const payload = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(payload?.error?.message || `AI provider HTTP ${response.status}`);
  const text = payload?.choices?.[0]?.message?.content;
  if (!text) throw new Error("AI provider returned no answer");
  return text;
}

export default {
  async fetch(request, env) {
    const allowed = env.ALLOWED_ORIGINS || "*";
    const headers = cors(request.headers.get("Origin"), allowed);
    if (request.method === "OPTIONS") return new Response(null, { status: 204, headers });
    const url = new URL(request.url);
    if (url.pathname === "/health") return json({ ok: true, service: "Universal AI Gateway", version: "1" }, 200, headers);
    if (url.pathname !== "/v1/chat" || request.method !== "POST") return json({ error: "Not found" }, 404, headers);

    try {
      const body = await request.json();
      const prompt = cleanText(body?.prompt);
      const pageUrl = cleanText(body?.url);
      let context = cleanText(body?.pageText);
      if (!prompt) return json({ error: "prompt is required" }, 400, headers);
      if (prompt.length > 4000) return json({ error: "prompt is too long" }, 413, headers);
      if (!context && env.ALLOW_PAGE_FETCH === "true" && pageUrl) context = await fetchPageText(pageUrl);
      if (!context) return json({ error: "pageText is required unless ALLOW_PAGE_FETCH=true" }, 400, headers);
      const answer = await callProvider(env, prompt, context);
      return json({ answer, model: env.AI_MODEL || "configured-provider", source: "Universal AI Gateway" }, 200, headers);
    } catch (error) {
      return json({ error: error?.message || "AI request failed" }, 502, headers);
    }
  }
};
