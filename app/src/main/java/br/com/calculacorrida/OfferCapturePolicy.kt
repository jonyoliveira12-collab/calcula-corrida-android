package br.com.calculacorrida

/** A readable upright route needs another live frame, not three rotations of the same image. */
object OfferCapturePolicy {
    fun needsRotationRetry(text:String):Boolean {
        val fare=Regex("""r\s*\$\s*\d""",RegexOption.IGNORE_CASE).containsMatchIn(text)
        val time=Regex("""\d+\s*min(?:uto)?s?\b""",RegexOption.IGNORE_CASE).containsMatchIn(text)
        val distance=Regex("""\d+(?:[,.]\d+)?\s*(?:km|m)\b""",RegexOption.IGNORE_CASE).containsMatchIn(text)
        // A clearly readable map/status page is already upright. Yield to the
        // next window instead of rotating the same non-offer several times.
        val readableWords=text.split(Regex("""\s+""")).count{word->word.count{it.isLetter()}>=3}
        return !(fare && time && distance) && readableWords<4
    }
}
