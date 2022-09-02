package dev.emortal.doors.pathfinding

import net.minestom.server.coordinate.Point

data class PathNode(val pos: Point, var g: Double = Double.MAX_VALUE, var h: Double = Double.MAX_VALUE, var parent: PathNode? = null, var directionToParent: Directions? = null) {
    val f get() = g + h
}