package dev.emortal.doors.pathfinding

import net.hollowcube.util.schem.Rotation
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec
import net.minestom.server.utils.Direction


fun Point.rotatePos(rotation: Rotation): Point {
    return when (rotation) {
        Rotation.NONE -> this
        Rotation.CLOCKWISE_90 -> Vec(-z(), y(), x())
        Rotation.CLOCKWISE_180 -> Vec(-x(), y(), -z())
        Rotation.CLOCKWISE_270 -> Vec(z(), y(), -x())
    }
}
fun Rotation.offset(): Vec = when (this) {
    Rotation.NONE -> Vec(0.0, 0.0, -1.0)
    Rotation.CLOCKWISE_180 -> Vec(0.0, 0.0, 1.0)
    Rotation.CLOCKWISE_90 -> Vec(1.0, 0.0, 0.0)
    Rotation.CLOCKWISE_270 -> Vec(-1.0, 0.0, 0.0)
}
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

fun Direction.asRotation(): Rotation = when (this) {
    Direction.NORTH -> Rotation.NONE
    Direction.EAST -> Rotation.CLOCKWISE_90
    Direction.SOUTH -> Rotation.CLOCKWISE_180
    Direction.WEST -> Rotation.CLOCKWISE_270
    else -> throw IllegalArgumentException("no")
}

fun Rotation.asDirection(): Direction = when (this) {
    Rotation.NONE -> Direction.NORTH
    Rotation.CLOCKWISE_90 -> Direction.EAST
    Rotation.CLOCKWISE_180 -> Direction.SOUTH
    Rotation.CLOCKWISE_270 -> Direction.WEST
    else -> throw IllegalArgumentException("no")
}