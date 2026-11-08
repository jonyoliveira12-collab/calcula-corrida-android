// Supabase Edge Function: analyze-offer
// Configure GEMINI_API_KEY as a Supabase Function secret. Never put it in the APK.
import { serve } from "https://deno.land/std@0.224.0/http/server.ts";

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") return Response.json({ error: "Method not allowed" }, { status: 405, headers: cors });

  const apiKey = Deno.env.get("GEMINI_API_KEY");
  if (!apiKey) return Response.json({ error: "Gemini ainda não foi configurado no servidor." }, { status: 503, headers: cors });

  const auth = req.headers.get("Authorization");
  if (!auth?.startsWith("Bearer ")) {
    return Response.json({ error: "Autenticação necessária." }, { status: 401, headers: cors });
  }

  // Validate Supabase session before using the Gemini quota.
  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const supabaseAnonKey = Deno.env.get("SUPABASE_ANON_KEY");
  if (!supabaseUrl || !supabaseAnonKey) {
    return Response.json({ error: "Autenticação do servidor não configurada." }, { status: 503, headers: cors });
  }
  const userCheck = await fetch(`${supabaseUrl}/auth/v1/user`, {
    headers: { "Authorization": auth, "apikey": supabaseAnonKey },
  });
  if (!userCheck.ok) {
    return Response.json({ error: "Sessão inválida ou expirada." }, { status: 401, headers: cors });
  }

  try {
    const input = await req.json();
    const fare = Number(input.fare);
    const totalKm = Number(input.totalKm);
    const minutes = Number(input.minutes);
    const perKm = Number(input.perKm);
    const perHour = Number(input.perHour);
    const rating = input.passengerRating == null ? null : Number(input.passengerRating);
    const classification = String(input.classification ?? "");
    if (![fare, totalKm, minutes, perKm, perHour].every(Number.isFinite) ||
        fare <= 0 || totalKm <= 0 || minutes <= 0 ||
        !["GOOD", "REVIEW", "BAD"].includes(classification)) {
      return Response.json({ error: "Dados da oferta inválidos." }, { status: 400, headers: cors });
    }

    const model = Deno.env.get("GEMINI_MODEL") || "gemini-2.5-flash";
    const response = await fetch(
      `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`,
      {
        method: "POST",
        headers: { "x-goog-api-key": apiKey, "Content-Type": "application/json" },
        body: JSON.stringify({
          systemInstruction: {
            parts: [{ text: "Você é o copiloto de um motorista de aplicativo no Brasil. Analise os números fornecidos sem inventar dados. O cálculo matemático e a classificação já foram feitos pelo aplicativo e são a fonte de verdade. Dê uma orientação curta em português brasileiro, com no máximo 18 palavras. Não mande aceitar ou rejeitar como ordem; explique fatores e incertezas." }]
          },
          contents: [{
            role: "user",
            parts: [{ text: JSON.stringify({ fare, totalKm, minutes, perKm, perHour, passengerRating: rating, classification }) }]
          }],
          generationConfig: { temperature: 0.2, maxOutputTokens: 110 }
        })
      }
    );
    const data = await response.json();
    if (!response.ok) {
      console.error("Gemini error", response.status, JSON.stringify(data).slice(0, 500));
      return Response.json({ error: "Não foi possível consultar o Gemini agora." }, { status: 502, headers: cors });
    }
    const insight = (data.candidates ?? [])
      .flatMap((candidate: any) => candidate.content?.parts ?? [])
      .map((part: any) => part.text ?? "")
      .join(" ").trim().slice(0, 240);
    return Response.json({ insight: insight || "Análise inteligente indisponível neste momento." }, { headers: cors });
  } catch (_error) {
    return Response.json({ error: "Solicitação inválida." }, { status: 400, headers: cors });
  }
});
