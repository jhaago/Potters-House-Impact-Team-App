package org.pottershouse.impactteam

data class AppIdentity(
    val applicationName: String,
    val protocolVersion: Int,
) {
    companion object {
        val default = AppIdentity(
            applicationName = "Potter's House Impact Team",
            protocolVersion = 1,
        )
    }
}

fun defaultApplicationName(): String = AppIdentity.default.applicationName
