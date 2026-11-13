package br.com.calculacorrida

object UpdatePolicy {
    fun isCompatibleUpdate(installedPackage: String, installedVersion: Long, installedCertificates: Set<String>,
        offeredPackage: String, offeredVersion: Long, offeredCertificate: String): Boolean =
        offeredPackage == installedPackage && offeredVersion > installedVersion &&
            installedCertificates.any { it.equals(offeredCertificate, ignoreCase = true) }
}
