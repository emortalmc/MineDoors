package dev.emortal.doors.schematic

import dev.emortal.doors.game.rotate180
import dev.emortal.doors.game.rotate270
import dev.emortal.doors.game.rotate90
import dev.hypera.scaffolding.schematic.impl.SpongeSchematic
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.instance.block.Block
import net.minestom.server.utils.Direction

data class RoomSchematic(val schem: SpongeSchematic, val name: String) {

    /**
     * Not rotated!
     */
    val doorPositions = mutableSetOf<Pair<Point, Block>>()

    private var minX = Integer.MAX_VALUE
    private var maxX = Integer.MIN_VALUE

    private var minY = Integer.MAX_VALUE
    private var maxY = Integer.MIN_VALUE

    private var minZ = Integer.MAX_VALUE
    private var maxZ = Integer.MIN_VALUE

    init {
        schem.apply { x, y, z, block ->
            if (block.isAir) return@apply
            if (block.compare(Block.GRASS_BLOCK)) return@apply

            if (block.compare(Block.DARK_OAK_DOOR) && block.getProperty("half") == "lower") {
                doorPositions.add(Pos(x.toDouble(), y.toDouble(), z.toDouble()) to block)
            }

            if (x > maxX) maxX = x
            else if (x < minX) minX = x

            if (y > maxY) maxY = y
            else if (y < minY) minY = y

            if (z > maxZ) maxZ = z
            else if (z < minZ) minZ = z
        }
    }

    fun bounds(position: Point, direction: Direction): RoomBounds {
        return when (direction) {
            Direction.NORTH -> {
                RoomBounds(
                    position.add(Vec(maxX.toDouble(), maxY.toDouble(), maxZ.toDouble())),
                    position.add(Vec(minX.toDouble(), minY.toDouble(), minZ.toDouble())),
                )
            }

            Direction.EAST -> {
                RoomBounds(
                    position.add(Vec(maxX.toDouble(), maxY.toDouble(), maxZ.toDouble()).rotate270()),
                    position.add(Vec(minX.toDouble(), minY.toDouble(), minZ.toDouble()).rotate270()),
                )
            }

            Direction.SOUTH -> {
                RoomBounds(
                    position.add(Vec(maxX.toDouble(), maxY.toDouble(), maxZ.toDouble()).rotate180()),
                    position.add(Vec(minX.toDouble(), minY.toDouble(), minZ.toDouble()).rotate180()),
                )
            }

            Direction.WEST -> {
                RoomBounds(
                    position.add(Vec(maxX.toDouble(), maxY.toDouble(), maxZ.toDouble()).rotate90()),
                    position.add(Vec(minX.toDouble(), minY.toDouble(), minZ.toDouble()).rotate90()),
                )
            }

            else -> {
                null
            }
        }!!
    }
}
