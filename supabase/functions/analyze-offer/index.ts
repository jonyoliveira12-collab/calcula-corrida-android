// AI adds short guidance. The client's local arithmetic remains the source of truth.
const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};
const reply = (body: unknown, status = 200) => Response.json(body, { status, headers: cors });

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    // Readiness only: never expose a credential or accept an unauthenticated AI request.
    return new Response("ok", { headers: { ...cors, "X-JHONDRIVER-AI-Configured": String(Boolean(Deno.env.get("GEMINI_API_KEY"))) } });
  }
  if (req.method !== "POST") return reply({ error: "Use POST." }, 405);
  const authorization = req.headers.get("Authorization");
  if (!authorization?.startsWith("Bearer ")) return reply({ error: "Autenticação necessária." }, 401);
  try {
    const url = Deno.env.get("SUPABASE_URL");
    const key = Deno.env.get("SUPABASE_ANON_KEY");
    if (!url || !key) return reply({ error: "Autenticação não configurada." }, 503);
    const user = await fetch(`${url}/auth/v1/user`, {
      headers: { Authorization: authorization, apikey: key }, signal: AbortSignal.timeout(4000),
    });
    if (!user.ok) return reply({ error: "Sessão inválida ou expirada." }, 401);
    const apiKey = Deno.env.get("GEMINI_API_KEY");
    if (!apiKey) return reply({ error: "IA não configurada no servidor." }, 503);
    const raw = await req.text();
    if (raw.length > 4096) return reply({ error: "Solicitação muito grande." }, 413);
    const input = JSON.parse(raw);
    const fare = Number(input.fare), totalKm = Number(input.totalKm), minutes = Number(input.minutes);
    const rating = input.passengerRating == null ? null : Number(input.passengerRating);
    const classification = String(input.classification ?? "");
    if (![fare, totalKm, minutes].every(Number.isFinite) || fare <= 0 || totalKm <= 0 || minutes <= 0 ||
        (rating != null && (!Number.isFinite(rating) || rating < 0 || rating > 5)) ||
        !["GOOD", "REVIEW", "BAD"].includes(classification)) return reply({ error: "Dados da oferta inválidos." }, 400);
    const metrics = { fare, totalKm, minutes, perKm: fare / totalKm, perHour: fare * 60 / minutes,
      passengerRating: rating, classification };
    const configuredModel = Deno.env.get("GEMINI_MODEL")?.trim();
    // The legacy model can remain listed while generateContent already returns 404.
    const model = !configuredModel || configuredModel === "gemini-2.5-flash"
      ? "gemini-3.5-flash-lite" : configuredModel;
    const response = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`, {
      method: "POST", headers: { "x-goog-api-key": apiKey, "Content-Type": "application/json" },
      body: JSON.stringify({
        systemInstruction: { parts: [{ text: "Você é o copiloto de um motorista no Brasil. Os indicadores matemáticos são a fonte de verdade. Dê orientação curta, com no máximo 18 palavras, em português. Não invente dados ausentes, não prometa lucro ou ganhos e não ordene aceitar ou recusar. Não refaça nem altere os números." }] },
        contents: [{ role: "user", parts: [{ text: JSON.stringify(metrics) }] }],
        generationConfig: { temperature: 0.2, maxOutputTokens: 256 },
      }), signal: AbortSignal.timeout(8000),
    });
    if (!response.ok) {
      console.error("AI provider HTTP status", response.status);
      return reply({ error: "IA temporariamente indisponível." }, response.status === 429 ? 429 : 502);
    }
    const data = await response.json();
    const insight = (data.candidates ?? []).flatMap((candidate: any) => candidate.content?.parts ?? [])
      .map((part: any) => part.text ?? "").join(" ").trim().slice(0, 240);
    return reply({ insight: insight || "IA sem orientação disponível; cálculos locais ativos." });
  } catch (error) {
    return reply({ error: error instanceof SyntaxError ? "Dados inválidos." : "IA temporariamente indisponível." }, error instanceof SyntaxError ? 400 : 502);
  }
});
