package dev.emortal.doors.schematic

import dev.emortal.doors.pathfinding.asRotation
import net.hollowcube.util.schem.Rotation
import net.hollowcube.util.schem.Schematic
import net.minestom.server.coordinate.Point
import net.minestom.server.instance.block.Block
import net.minestom.server.utils.Direction



data class RoomSchematic(val schem: Schematic, val name: String) {

    /**
     * Not rotated!
     */
    val doorPositions = mutableSetOf<Pair<Point, Rotation>>()

    init {
        schem.apply(Rotation.NONE) { pos, block ->
            if (block.compare(Block.DARK_OAK_DOOR) && block.getProperty("half") == "lower") {
                doorPositions.add(pos to Direction.valueOf(block.getProperty("facing").uppercase()).asRotation())
            }
        }
    }

    fun bounds(position: Point, rotation: Rotation): RoomBounds {
        return RoomBounds(schem.offset(rotation).add(position), schem.size(rotation).add(schem.offset(rotation)).add(position))
    }
}
