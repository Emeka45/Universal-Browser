# Universal AI Gateway

Secure, provider-agnostic AI gateway for Universal Browser.

## Contract

`POST /v1/chat`

```json
{
  "url": "https://example.com/article",
  "prompt": "Summarize this page",
  "pageText": "optional extracted page text",
  "source": "Universal Browser"
}
```

Response:

```json
{
  "answer": "...",
  "model": "...",
  "source": "Universal AI Gateway"
}
```

The gateway uses an OpenAI-compatible `/chat/completions` provider endpoint. `AI_BASE_URL`, `AI_MODEL`, and `AI_API_KEY` are kept server-side. The API key is never placed in the Android APK.

## Cloudflare Workers deployment

1. Install Wrangler.
2. Set `AI_BASE_URL` and `AI_API_KEY` as Wrangler secrets.
3. Change `AI_MODEL` to the model you want to use.
4. Keep `ALLOW_PAGE_FETCH=false` when the Android client supplies extracted text. If URL fetching is enabled, only public HTTP(S) pages are accepted and local hostnames are rejected.
5. Deploy with `wrangler deploy`.

Cloudflare Worker environment bindings are supplied to the `fetch(request, env)` handler. Secrets should be stored as Worker secrets rather than committed to the repository.

CI integration validation: Android media integration is applied automatically by `.github/workflows/media-ai-upgrade.yml`.
