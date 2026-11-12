package br.com.calculacorrida

/** Identifies offer layouts without confusing ongoing-trip navigation with an offer. */
object IncomingOfferPolicy {
    /** Markers of the driver's 99 offer card, including negotiated offers. */
    fun hasNinetyNineOfferMarker(text:String):Boolean {
        val n=java.text.Normalizer.normalize(text,java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"),"").lowercase(java.util.Locale.ROOT)
        return listOf("nao afeta a ta","tarifa base dinamica","negocia","pgto. no app",
            "pgto no app","pagamento no app","cartao verif","perfil essencial","perfil premium").any{n.contains(it)}
    }

    fun isIncoming(pkg:String,text:String):Boolean {
        val driverName = DriverPackages.name(pkg) ?: return false
        val n=java.text.Normalizer.normalize(text,java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"),"").lowercase(java.util.Locale.ROOT)
        if(listOf("encerrar viagem","finalizar viagem","em viagem","cheguei").any{n.contains(it)})return false
        if(!Regex("""r\s*\$\s*[0-9]""").containsMatchIn(n))return false
        if(Regex("""\b(aceitar|aceite|accept|aceptar|tenho\s+interesse|confirmar)\b""").containsMatchIn(n))return true
        // 99 displays an offer countdown and route legs, without an 'Aceitar' label.
        val is99=driverName=="99"
        val marker=hasNinetyNineOfferMarker(n)
        val legs=Regex("""\d+\s*min(?:uto)?s?\s*\(\s*\d+(?:[,.]\d+)?\s*km""").findAll(n).count()
        val times=Regex("""\d+\s*min(?:uto)?s?\b""").findAll(n).count()
        val distances=Regex("""\d+(?:[,.]\d+)?\s*(?:km|m)\b""").findAll(n).count()
        return is99 && marker && (legs>=2 || (times>=2 && distances>=2))
    }
}
