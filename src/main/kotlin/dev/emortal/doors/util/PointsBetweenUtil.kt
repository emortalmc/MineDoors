package dev.emortal.doors.util

import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import kotlin.math.max
import kotlin.math.min

/**
 * @return the points between [pos1] and [pos2]
 */
fun pointsBetween(pos1: Point, pos2: Point): MutableList<Point> {
    val points = mutableListOf<Point>()

    val minX = min(pos1.blockX(), pos2.blockX())
    val maxX = max(pos1.blockX(), pos2.blockX())
    val minY = min(pos1.blockY(), pos2.blockY())
    val maxY = max(pos1.blockY(), pos2.blockY())
    val minZ = min(pos1.blockZ(), pos2.blockZ())
    val maxZ = max(pos1.blockZ(), pos2.blockZ())

    for (x in minX..maxX) for (y in minY..maxY) for (z in minZ..maxZ) {
        points.add(Pos(x.toDouble(), y.toDouble(), z.toDouble()))
    }

    return points
}