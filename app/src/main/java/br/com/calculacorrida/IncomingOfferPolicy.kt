package br.com.calculacorrida

/** Identifies offer layouts without confusing ongoing-trip navigation with an offer. */
object IncomingOfferPolicy {
    fun isIncoming(pkg:String,text:String):Boolean {
        val driverName = DriverPackages.name(pkg) ?: return false
        val n=java.text.Normalizer.normalize(text,java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"),"").lowercase(java.util.Locale.ROOT)
        if(listOf("encerrar viagem","finalizar viagem","em viagem","cheguei").any{n.contains(it)})return false
        if(!Regex("""r\s*\$\s*[0-9]""").containsMatchIn(n))return false
        if(Regex("""\b(aceitar|aceite|accept|aceptar|tenho\s+interesse|confirmar)\b""").containsMatchIn(n))return true
        // 99 displays an offer countdown and route legs, without an 'Aceitar' label.
        val is99=driverName=="99"
        val marker=n.contains("nao afeta a ta") || n.contains("tarifa base dinamica inicial")
        val legs=Regex("""\d+\s*min(?:uto)?s?\s*\(\s*\d+(?:[,.]\d+)?\s*km""").findAll(n).count()
        return is99 && marker && legs>=2
    }
}
