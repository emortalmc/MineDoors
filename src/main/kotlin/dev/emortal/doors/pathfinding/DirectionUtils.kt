package dev.emortal.doors.pathfinding

import net.minestom.server.coordinate.Vec
import net.minestom.server.utils.Direction

fun Directions.offset(): Vec = Vec(offX.toDouble(), offY.toDouble(), offZ.toDouble())
fun Direction.offset(): Vec = Vec(normalX().toDouble(), normalY().toDouble(), normalZ().toDouble())
fun Direction.rotate(): Direction = Direction.HORIZONTAL[(Direction.HORIZONTAL.indexOf(this) + 1) % Direction.HORIZONTAL.size]
fun Direction.yaw(): Float = when (this) {
    Direction.NORTH -> 180f
    Direction.EAST -> -90f
    Direction.SOUTH -> 0f
    Direction.WEST -> 90f
    else -> 0f
}