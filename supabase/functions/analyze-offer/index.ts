// Supabase Edge Function: analyze-offer
// Configure OPENAI_API_KEY as a Supabase Function secret. Never put it in the APK.
import { serve } from "https://deno.land/std@0.224.0/http/server.ts";

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") return Response.json({ error: "Method not allowed" }, { status: 405, headers: cors });

  const apiKey = Deno.env.get("OPENAI_API_KEY");
  if (!apiKey) return Response.json({ error: "IA ainda não configurada no servidor." }, { status: 503, headers: cors });

  const auth = req.headers.get("Authorization");
  if (!auth?.startsWith("Bearer ")) return Response.json({ error: "Autenticação necessária." }, { status: 401, headers: cors });

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

    const response = await fetch("https://api.openai.com/v1/responses", {
      method: "POST",
      headers: { "Authorization": `Bearer ${apiKey}`, "Content-Type": "application/json" },
      body: JSON.stringify({
        model: "gpt-4.1-mini",
        max_output_tokens: 110,
        input: [
          { role: "system", content: "Você é o copiloto de um motorista de aplicativo no Brasil. Analise os números fornecidos sem inventar dados. O cálculo matemático e a classificação já foram feitos pelo app e são a fonte de verdade. Dê uma orientação curta em português brasileiro, no máximo 18 palavras. Não mande aceitar/rejeitar como ordem; destaque fatores e incerteza." },
          { role: "user", content: JSON.stringify({ fare, totalKm, minutes, perKm, perHour, passengerRating: rating, classification }) }
        ]
      })
    });
    const data = await response.json();
    if (!response.ok) {
      console.error("OpenAI error", response.status, JSON.stringify(data).slice(0, 500));
      return Response.json({ error: "Não foi possível consultar a IA agora." }, { status: 502, headers: cors });
    }
    const text = (data.output ?? []).flatMap((item: any) => item.content ?? [])
      .filter((part: any) => part.type === "output_text")
      .map((part: any) => part.text).join(" ").trim().slice(0, 240);
    return Response.json({ insight: text || "Análise inteligente indisponível neste momento." }, { headers: cors });
  } catch (_error) {
    return Response.json({ error: "Solicitação inválida." }, { status: 400, headers: cors });
  }
});