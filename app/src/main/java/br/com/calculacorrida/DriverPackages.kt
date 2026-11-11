package br.com.calculacorrida

import java.util.Locale

/** Only known driver applications; passenger apps must never be read. */
object DriverPackages {
    fun name(pkg: String): String? = when (pkg.lowercase(Locale.ROOT)) {
        "com.ubercab.driver" -> "UBER"
        "com.app99.driver", "com.didiglobal.driver" -> "99"
        "sinet.startup.indriver" -> "INDRIVE"
        "br.com.ifood.driver.app" -> "IFOOD"
        else -> null
    }
}
