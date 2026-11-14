package br.com.calculacorrida

/** A readable upright route needs another live frame, not three rotations of the same image. */
object OfferCapturePolicy {
    fun needsRotationRetry(text:String):Boolean {
        val fare=Regex("""r\s*\$\s*\d""",RegexOption.IGNORE_CASE).containsMatchIn(text)
        val time=Regex("""\d+\s*min(?:uto)?s?\b""",RegexOption.IGNORE_CASE).containsMatchIn(text)
        val distance=Regex("""\d+(?:[,.]\d+)?\s*(?:km|m)\b""",RegexOption.IGNORE_CASE).containsMatchIn(text)
        return !(fare && time && distance)
    }
}
