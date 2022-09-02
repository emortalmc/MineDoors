package dev.emortal.doors.raycast

import net.minestom.server.coordinate.Point
import net.minestom.server.entity.Entity

data class RaycastResult(
    val resultType: RaycastResultType,
    val hitEntity: Entity?,
    val hitPosition: Point?,
)