package br.com.calculacorrida

/** Detects another JHONDRIVER installation with an enabled accessibility service. */
object ReaderVersions {
    private const val BASE_PACKAGE="br.com.calculacorrida"

    fun hasOtherEnabledService(enabledComponents:String?,currentPackage:String):Boolean =
        enabledComponents.orEmpty().split(':').any { component ->
            val pkg=component.substringBefore('/').trim()
            pkg.isNotEmpty() && pkg!=currentPackage &&
                (pkg==BASE_PACKAGE || pkg.startsWith("$BASE_PACKAGE."))
        }
}
