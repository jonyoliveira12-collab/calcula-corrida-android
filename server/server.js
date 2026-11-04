import express from "express";

const app = express();
app.use(express.json({ limit: "64kb" }));
const PORT = process.env.PORT || 3000;
const GEMINI_API_KEY = process.env.GEMINI_API_KEY || "";

function number(value) {
  if (typeof value === "number" && Number.isFinite(value)) return value;
  if (typeof value !== "string") return null;
  const normalized = value.replace(/[^0-9,.-]/g, "").replace(",", ".");
  const parsed = Number(normalized);
  return Number.isFinite(parsed) ? parsed : null;
}

function extractFromText(text) {
  // Deterministic fallback. The client should send explicit fields whenever accessibility exposes them.
  const fareMatch = text.match(/R\$\s*([0-9]+(?:[.,][0-9]{1,2})?)/i);
  const km = [...text.matchAll(/([0-9]+(?:[.,][0-9]+)?)\s*km/gi)].map(m => number(m[1]));
  const min = [...text.matchAll(/([0-9]+)\s*(?:min|minutos?)/gi)].map(m => number(m[1]));
  const ratingMatch = text.match(/(?:nota|avaliação|rating)\s*[:：]?\s*([0-5][.,][0-9]{1,2})/i);
  if (!fareMatch || km.length === 0 || min.length === 0) return null;
  // Do not guess which displayed distance is pickup vs trip when the offer layout is ambiguous.
  if (km.length < 2) return { fare: number(fareMatch[1]), totalKm: km[0], totalMinutes: min[0], passengerRating: ratingMatch?.[1] ?? null, confidence: "low" };
  return { fare: number(fareMatch[1]), pickupKm: km[0], tripKm: km.slice(1).reduce((a,b)=>a+b,0), totalMinutes: min[0], passengerRating: ratingMatch?.[1] ?? null, confidence: "medium" };
}

async function interpretWithGemini(text) {
  if (!GEMINI_API_KEY) return null;
  const response = await fetch("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=" + GEMINI_API_KEY, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      contents: [{ role: "user", parts: [{ text:
        "Extraia somente dados explicitamente visíveis nesta oferta de corrida. Não invente nem estime campos. Responda JSON com fare (number|null), pickupKm (number|null), tripKm (number|null), pickupMinutes (number|null), tripMinutes (number|null), passengerRating (number|null), confidence (high|medium|low). Se não houver certeza, null. Texto OCR: " + text
      }]}],
      generationConfig: { responseMimeType: "application/json", temperature: 0 }
    })
  });
  if (!response.ok) throw new Error("Gemini HTTP " + response.status);
  const data = await response.json();
  const raw = data.candidates?.[0]?.content?.parts?.[0]?.text;
  return raw ? JSON.parse(raw) : null;
}

app.get("/health", (_req, res) => res.json({ ok: true, service: "JHONDRIVER online analysis", version: "1.0.0" }));

app.post("/v1/analyze", async (req, res) => {
  try {
    const { platform, ocrText = "", offer = {}, minPerKm = 1.8, minPerHour = 35 } = req.body ?? {};
    if (!["uber", "99", "unknown"].includes(String(platform).toLowerCase())) return res.status(400).json({ error: "platform must be uber, 99 or unknown" });
    if (typeof ocrText !== "string" || ocrText.length > 12000) return res.status(400).json({ error: "invalid ocrText" });
    let parsed = Object.keys(offer).length ? offer : null;
    if (!parsed && ocrText.trim()) {
      try { parsed = await interpretWithGemini(ocrText); } catch (error) {
        console.error("Gemini interpretation failed:", error.message);
      }
      if (!parsed) parsed = extractFromText(ocrText);
    }
    if (!parsed) return res.status(422).json({ status: "insufficient_data", message: "Offer details could not be read reliably." });

    const fare = number(parsed.fare);
    const pickupKm = number(parsed.pickupKm);
    const tripKm = number(parsed.tripKm);
    const pickupMinutes = number(parsed.pickupMinutes) ?? 0;
    const tripMinutes = number(parsed.tripMinutes);
    const totalKm = number(parsed.totalKm) ?? (pickupKm != null && tripKm != null ? pickupKm + tripKm : null);
    const totalMinutes = number(parsed.totalMinutes) ?? (tripMinutes != null ? pickupMinutes + tripMinutes : null);
    const rating = number(parsed.passengerRating);
    if (fare == null || fare <= 0 || totalKm == null || totalKm <= 0 || totalMinutes == null || totalMinutes <= 0) {
      return res.status(422).json({ status: "insufficient_data", parsed, message: "Fare, total distance and total time are required. No values were guessed." });
    }
    const perKm = fare / totalKm;
    const perHour = fare * 60 / totalMinutes;
    const kmLimit = number(minPerKm) ?? 1.8;
    const hourLimit = number(minPerHour) ?? 35;
    const ratingStatus = perKm >= kmLimit && perHour >= hourLimit ? "good"
      : perKm < kmLimit * .85 || perHour < hourLimit * .85 ? "bad" : "review";
    res.json({
      status: "ok", platform, source: parsed.confidence === "low" ? "ocr_low_confidence" : "online_analysis",
      fare, pickupKm, tripKm, pickupMinutes, tripMinutes, totalKm, totalMinutes,
      passengerRating: rating, perKm: Number(perKm.toFixed(2)), perHour: Number(perHour.toFixed(2)),
      classification: ratingStatus, confidence: parsed.confidence ?? "medium",
      explanation: "R$/km uses total distance including pickup; R$/hour uses total time including pickup when supplied."
    });
  } catch (error) {
    console.error(error);
    res.status(500).json({ error: "analysis_failed" });
  }
});

app.listen(PORT, "0.0.0.0", () => console.log("JHONDRIVER API listening on " + PORT));
