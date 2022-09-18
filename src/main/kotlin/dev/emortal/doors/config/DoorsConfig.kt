package dev.emortal.doors.config

import kotlinx.serialization.Serializable

@Serializable
data class DoorsConfig(val donators: List<String>)