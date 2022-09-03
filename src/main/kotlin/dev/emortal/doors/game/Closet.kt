package dev.emortal.doors.game

import dev.emortal.doors.pathfinding.offset
import dev.emortal.doors.pathfinding.rotate
import dev.emortal.doors.pathfinding.yaw
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.instance.block.Block
import net.minestom.server.utils.Direction

data class Closet(val doorDirection: Direction, val rotatedDir: Direction, val yOffset: Double, val pos: Pos) {
    companion object {
        fun get(block: Block, blockPosition: Point): Closet {
            val doorDirection = Direction.valueOf(block.getProperty("facing").uppercase())
            val rotatedDir = doorDirection.rotate()
            val yOffset = if (block.getProperty("half") == "upper") -1.0 else 0.0

            return Closet(doorDirection, rotatedDir, yOffset, Pos(blockPosition.add(0.5, yOffset, 0.5)
                .sub(doorDirection.offset())
                .let {
                    if (block.getProperty("hinge") == "left") it.add(rotatedDir.normalX().toDouble() * 0.5, 0.0, rotatedDir.normalZ().toDouble() * 0.5)
                    else it.sub(rotatedDir.normalX().toDouble() * 0.5, 0.0, rotatedDir.normalZ().toDouble() * 0.5)
                },
                doorDirection.yaw(), 0f))
        }
    }
}