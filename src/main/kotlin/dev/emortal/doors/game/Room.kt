package dev.emortal.doors.game

import dev.emortal.doors.applyRotationToBlock
import dev.emortal.doors.block.SignHandler
import dev.emortal.doors.block.SingleChestHandler
import dev.emortal.doors.doorSchem
import dev.emortal.doors.game.DoorsGame.Companion.applyDoor
import dev.emortal.doors.game.RoomBounds.Companion.isOverlapping
import dev.emortal.doors.pathfinding.offset
import dev.emortal.doors.schematics
import dev.hypera.scaffolding.region.Region
import dev.hypera.scaffolding.schematic.impl.SpongeSchematic
import net.kyori.adventure.text.Component
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.metadata.other.PaintingMeta
import net.minestom.server.instance.Instance
import net.minestom.server.instance.batch.AbsoluteBlockBatch
import net.minestom.server.instance.batch.BatchOption
import net.minestom.server.instance.block.Block
import net.minestom.server.tag.Tag
import net.minestom.server.utils.Direction
import org.jglrxavpok.hephaistos.nbt.NBTCompound
import org.jglrxavpok.hephaistos.parser.SNBTParser
import world.cepi.kstom.util.asPos
import java.io.StringReader
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.ThreadLocalRandom


val SpongeSchematic.offset: Point get() = Vec(offsetX.toDouble(), offsetY.toDouble(), offsetZ.toDouble())
// requires direction to account for overlap
fun SpongeSchematic.size(direction: Direction): Point = Vec(width.toDouble(), height.toDouble(), length.toDouble())

fun Point.rotate90() = Vec(z(), y(), -x())
fun Point.rotate180() = Vec(-x(), y(), -z())
fun Point.rotate270() = Vec(-z(), y(), x())

fun SpongeSchematic.doors(position: Point, direction: Direction): MutableSet<Pair<Point, Direction>> {
    val doorPositions = mutableSetOf<Pair<Point, Direction>>()

    apply { x, y, z, block ->
        val rotatedBlock = applyRotationToBlock(position, x, y, z, block, direction, this)

        if (rotatedBlock.second.compare(Block.DARK_OAK_DOOR) && rotatedBlock.second.getProperty("half") == "lower") {
            doorPositions.add(rotatedBlock.first to Direction.valueOf(rotatedBlock.second.getProperty("facing").uppercase()))
        }

    }

    return doorPositions
}

fun SpongeSchematic.bounds(position: Point, direction: Direction): RoomBounds {
    var minX = Integer.MAX_VALUE
    var maxX = Integer.MIN_VALUE

    var minY = Integer.MAX_VALUE
    var maxY = Integer.MIN_VALUE

    var minZ = Integer.MAX_VALUE
    var maxZ = Integer.MIN_VALUE

    apply { x, y, z, block ->
        if (block.isAir) return@apply
        if (block.compare(Block.GRASS_BLOCK)) return@apply

        if (x > maxX) maxX = x
        else if (x < minX) minX = x

        if (y > maxY) maxY = y
        else if (y < minY) minY = y

        if (z > maxZ) maxZ = z
        else if (z < minZ) minZ = z
    }

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

        else -> { null }
    }!!

//    return RoomBounds(
//        Vec(maxX.toDouble() + 1.0, maxY.toDouble(), maxZ.toDouble() + 1.0).add(position),
//        Vec(minX.toDouble(), minY.toDouble(), minZ.toDouble()).add(position),
//    )
}


//fun SpongeSchematic.bounds(position: Point, direction: Direction): RoomBounds =
//    when (direction) {
//        Direction.NORTH -> {
//            RoomBounds(
//                position.add(this.size(direction).add(this.offset).sub(1.0, 0.0, 1.0)),
//                position.add(this.offset.add(1.0, 0.0, 1.0))
//            )
//        }
//        Direction.EAST -> {
//            RoomBounds(
//                position.add(this.size(direction).add(this.offset).rotate270().sub(1.0, 0.0, 1.0)),
//                position.add(this.offset.rotate270().add(1.0, 0.0, 1.0))
//            )
//        }
//        Direction.SOUTH -> {
//            RoomBounds(
//                position.add(this.size(direction).add(this.offset).rotate180().sub(1.0, 0.0, 1.0)),
//                position.add(this.offset.rotate180().add(1.0, 0.0, 1.0))
//            )
//        }
//        Direction.WEST -> {
//            RoomBounds(
//                position.add(this.size(direction).add(this.offset).rotate90().sub(1.0, 0.0, 1.0)),
//                position.add(this.offset.rotate90().add(1.0, 0.0, 1.0))
//            )
//        }
//
//        else -> { null }
//    }!!

class Room(val game: DoorsGame, val instance: Instance, val position: Point, val direction: Direction) {

    companion object {
        val chestXTag = Tag.Integer("chestX")
        val chestYTag = Tag.Integer("chestY")
        val chestZTag = Tag.Integer("chestZ")

        val lightBlockTypes = setOf<Block>(
            Block.LANTERN,
            Block.REDSTONE_LAMP,
            Block.CANDLE,
            Block.LIGHT
        )
    }

    lateinit var schematic: SpongeSchematic

    val lightBlocks = CopyOnWriteArrayList<Pair<Point, Block>>()
    val chests = CopyOnWriteArrayList<Point>()
    val entityIds = CopyOnWriteArraySet<Int>()

    var removalBatch: AbsoluteBlockBatch? = null


    val number = game.roomNum.incrementAndGet()

    fun applyRoom(schemList: Collection<SpongeSchematic> = schematics): CompletableFuture<Void>? {
        val rand = ThreadLocalRandom.current()

        val randomSchem = schematics.shuffled().firstOrNull { schem ->
            val bounds = schem.bounds(position, direction)
            val doors = schem.doors(position, direction)

            // check that the attempted schem
            // - will not overlap with any rooms
            // - have atleast one door available to spawn next room from
            val noRooms = game.rooms.none { otherRoom ->
                if (otherRoom.number + 1 == number) return@none false

                val isOverlapping = isOverlapping(bounds, otherRoom.schematic.bounds(otherRoom.position, otherRoom.direction))
                val allDoorsOverlapping = doors.all { door ->
                    val doorbound = schem.bounds(door.first, door.second)

                    isOverlapping(doorbound, otherRoom.schematic.bounds(otherRoom.position, otherRoom.direction))
                }

                //println("overlap: ${isOverlapping}, doors: ${allDoorsOverlapping}")

                isOverlapping || allDoorsOverlapping
                //allDoorsOverlapping
            }

            noRooms
        }



        if (randomSchem == null) {
            instance.sendMessage(Component.text("No more schematics!!"))
            return null
        }

        val doorPositions = mutableSetOf<Pair<Point, Direction>>()
        val doorAboveBlocks = mutableSetOf<Block>()
        val paintingPositions = mutableSetOf<Pair<Point, Direction>>()

        val batch = AbsoluteBlockBatch(BatchOption().setCalculateInverse(true))

        val blockList = mutableListOf<Region.Block>()

        randomSchem.apply { x, y, z, block ->
            val rotatedBlock = applyRotationToBlock(position, x, y, z, block, direction, randomSchem)

            blockList.add(Region.Block(rotatedBlock.first.asPos(), rotatedBlock.second.stateId()))

            // Record doors
            if (rotatedBlock.second.compare(Block.DARK_OAK_DOOR) && rotatedBlock.second.getProperty("half") == "lower") {
                doorPositions.add(rotatedBlock.first to Direction.valueOf(rotatedBlock.second.getProperty("facing").uppercase()))
            }

            // Initialize chests
            if (rotatedBlock.second.compare(Block.CHEST)) {
                val direction = Direction.valueOf(rotatedBlock.second.getProperty("facing").uppercase())

                chests.add(rotatedBlock.first)
                batch.setBlock(rotatedBlock.first, rotatedBlock.second.withHandler(SingleChestHandler()))
                return@apply
            }

            // Paintings
            if (rotatedBlock.second.compare(Block.WARPED_WALL_SIGN)) {
                val direction = Direction.valueOf(rotatedBlock.second.getProperty("facing").uppercase())

                paintingPositions.add(rotatedBlock.first to direction)

                return@apply
            }

            if (lightBlockTypes.any { it.compare(rotatedBlock.second) }) {
                lightBlocks.add(rotatedBlock)
            }

            if (!instance.getBlock(rotatedBlock.first).compare(Block.AIR)) return@apply

            batch.setBlock(rotatedBlock.first, rotatedBlock.second)
        }

        val availableDoorPositions = doorPositions.filter { door ->
            if (door.second == Direction.SOUTH) return@filter false

            val doorbound = randomSchem.bounds(door.first, door.second)

            game.rooms.none { otherRoom ->
                if (otherRoom.number + 1 == number) return@none false
                isOverlapping(doorbound, otherRoom.schematic.bounds(otherRoom.position, otherRoom.direction))
            }
        }

        if (availableDoorPositions.isEmpty()) {
            batch.clear()

            val newSchemList = schemList.filter { it != randomSchem }.filter {
                val bounds = it.bounds(position, direction)
                game.rooms.none { otherRoom ->
                    if (otherRoom.number + 1 == number) return@none false
                    isOverlapping(bounds, otherRoom.schematic.bounds(otherRoom.position, otherRoom.direction))
                }
            }
            if (newSchemList.isEmpty()) {
                println("NO MORE ROOMS ARE AVAILABLE")
                return null
            }

            return applyRoom(newSchemList)
        }

        val randomDoor = availableDoorPositions.random()

        // loop through other doors and remove them

        doorPositions.forEachIndexed { i, door ->
            if (i == doorPositions.indexOf(randomDoor)) return@forEachIndexed // ignore the picked door

            val block = Block.fromStateId(blockList.first { it.position.samePoint(door.first.add(0.0, 2.0, 0.0)) }.stateId)!!
            batch.setBlock(door.first, block)
            batch.setBlock(door.first.add(0.0, 1.0, 0.0), block)
        }

        applyDoor(game, batch, doorSchem, randomDoor.first, randomDoor.second, instance)

        paintingPositions.forEach {
            val painting = Entity(EntityType.PAINTING)
            val meta = painting.entityMeta as PaintingMeta
            meta.motive = PaintingMeta.Motive.values().filter { it.width <= 3*16 && it.height <= 2*16 }.random()
            meta.direction = it.second

            painting.setNoGravity(true)

            when (it.second) {
                Direction.SOUTH, Direction.EAST -> painting.setInstance(instance, it.first.add(0.5, 0.0, 0.5))
                Direction.NORTH, Direction.WEST -> painting.setInstance(instance, it.first.add(1.5, 0.0, 1.5))
                else -> {}
            }

            entityIds.add(painting.entityId)

            batch.setBlock(it.first, Block.AIR)
        }

        val data: NBTCompound = SNBTParser(
            StringReader("{\"GlowingText\":1B,\"Color\":\"white\",\"Text1\":\"{\\\"text\\\":\\\"\\\"}\"," +
                "\"Text2\":\"{\\\"text\\\":\\\"Room ${number + 1}\\\"}\",\"Text3\":\"{\\\"text\\\":\\\"\\\"}\",\"Text4\":\"{\\\"text\\\":\\\"\\\"}\"}")
        ).parse() as NBTCompound

        batch.setBlock(
            randomDoor.first.add(0.0, 2.0, 0.0).sub(randomDoor.second.offset()),
            Block.OAK_WALL_SIGN
                .withProperties(mapOf("facing" to randomDoor.second.opposite().name.lowercase()))
                .withHandler(SignHandler)
                .withNbt(data)
        )

        schematic = randomSchem

        val future = CompletableFuture<Void>()
        removalBatch = batch.apply(instance) {
            future.complete(null)
        }!!
        return future
    }

}