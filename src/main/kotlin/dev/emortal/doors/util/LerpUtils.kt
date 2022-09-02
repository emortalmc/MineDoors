package dev.emortal.doors.util

import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec

fun lerp(low: Double, high: Double, v: Double): Double {
    val v = v.coerceIn(0.0, 1.0)
    val delta = high - low
    return low + v * delta
}

fun Point.lerp(other: Point, v: Double): Vec = Vec(lerp(x(), other.x(), v), lerp(y(), other.y(), v), lerp(z(), other.z(), v))