package dev.emortal.doors.game

import dev.emortal.doors.applyRotationToBlock
import dev.emortal.doors.block.ChestHandler
import dev.emortal.doors.block.SignHandler
import dev.emortal.doors.doorSchem
import dev.emortal.doors.game.ChestLoot.addRandomly
import dev.emortal.doors.game.DoorsGame.Companion.applyDoor
import dev.emortal.doors.pathfinding.offset
import dev.emortal.doors.pathfinding.rotate
import dev.emortal.doors.relight
import dev.emortal.doors.schematic.RoomBounds.Companion.isOverlapping
import dev.emortal.doors.schematic.RoomSchematic
import dev.emortal.doors.schematics
import dev.hypera.scaffolding.region.Region
import dev.hypera.scaffolding.schematic.impl.SpongeSchematic
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.metadata.other.PaintingMeta
import net.minestom.server.instance.Instance
import net.minestom.server.instance.batch.AbsoluteBlockBatch
import net.minestom.server.instance.batch.BatchOption
import net.minestom.server.instance.block.Block
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.utils.Direction
import net.minestom.server.utils.time.TimeUnit
import org.jglrxavpok.hephaistos.nbt.NBTCompound
import org.jglrxavpok.hephaistos.parser.SNBTParser
import org.tinylog.kotlin.Logger
import world.cepi.kstom.adventure.noItalic
import world.cepi.kstom.util.asPos
import java.io.StringReader
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.ThreadLocalRandom
import kotlin.collections.set


val SpongeSchematic.offset: Point get() = Vec(offsetX.toDouble(), offsetY.toDouble(), offsetZ.toDouble())
// requires direction to account for overlap
fun SpongeSchematic.size(direction: Direction): Point = Vec(width.toDouble(), height.toDouble(), length.toDouble())

fun Point.rotate90() = Vec(z(), y(), -x())
fun Point.rotate180() = Vec(-x(), y(), -z())
fun Point.rotate270() = Vec(-z(), y(), x())

//fun SpongeSchematic.doors(position: Point, direction: Direction): MutableSet<Pair<Point, Direction>> {
//    val doorPositions = mutableSetOf<Pair<Point, Direction>>()
//
//    apply { x, y, z, block ->
//        val rotatedBlock = applyRotationToBlock(position, x, y, z, block, direction, this)
//
//        if (rotatedBlock.second.compare(Block.DARK_OAK_DOOR) && rotatedBlock.second.getProperty("half") == "lower") {
//            doorPositions.add(rotatedBlock.first to Direction.valueOf(rotatedBlock.second.getProperty("facing").uppercase()))
//        }
//
//    }
//
//    return doorPositions
//}

class Room(val game: DoorsGame, val instance: Instance, val position: Point, val direction: Direction) {

    companion object {
        val lightBlockTypes = setOf<Block>(
            Block.LANTERN,
            Block.REDSTONE_LAMP,
            Block.CANDLE,
            Block.LIGHT
        )
    }

    lateinit var schematic: RoomSchematic

    var keyRoom = false

    val lightBlocks = CopyOnWriteArrayList<Pair<Point, Block>>()
    val chests = CopyOnWriteArrayList<Pair<Point, ChestHandler>>()
    val entityIds = CopyOnWriteArraySet<Int>()

    var closets: Int = 0

    val number = game.roomNum.incrementAndGet()

    // 10% chance for dark room
    var darkRoom = number > 7 && ThreadLocalRandom.current().nextDouble() < 0.10

    fun applyRoom(schemList: Collection<RoomSchematic> = schematics.toMutableList(), force: Boolean = false): CompletableFuture<Void>? {
        val randomSchem = if (force) {
            schemList.random()
        } else {
            schemList.shuffled().firstOrNull { schem ->
                val bounds = schem.bounds(position, direction)
                if (bounds.outOfBounds()) {
                    Logger.info("out of bounds")
                    Logger.info(bounds.toString())
                    return@firstOrNull false
                }

                val doors = schem.doorPositions.map { applyRotationToBlock(position, it.first.blockX(), it.first.blockY(), it.first.blockZ(), it.second, direction, schem.schem) }

                // check that the attempted schem
                // - will not overlap with any rooms
                // - have atleast one door available to spawn next room from
                val noRooms = game.rooms.none { otherRoom ->
                    if (otherRoom.number + 1 == number) return@none false

                    val otherRoomBounds = otherRoom.schematic.bounds(otherRoom.position, otherRoom.direction)

                    val isOverlapping = isOverlapping(bounds, otherRoomBounds)
                    val allDoorsOverlapping = doors.all { door ->
                        val doorbound = schem.bounds(door.first, Direction.valueOf(door.second.getProperty("facing").uppercase()))

                        isOverlapping(doorbound, otherRoomBounds)
                    }

                    Logger.info("overlap ${isOverlapping}")
                    Logger.info("doors overlap ${allDoorsOverlapping}")
                    isOverlapping || allDoorsOverlapping
                }

                noRooms
            }
        }



        if (randomSchem == null) {
            Logger.info("No more schematics")
            return null
        }

        val doorPositions = randomSchem.doorPositions.map { applyRotationToBlock(position, it.first.blockX(), it.first.blockY(), it.first.blockZ(), it.second, direction, randomSchem.schem) }
        val availableDoorPositions = if (force) doorPositions else doorPositions.filter { door ->
            val doorDir = Direction.valueOf(door.second.getProperty("facing").uppercase())
            if (doorDir == Direction.SOUTH) return@filter false

            // TODO - doesn't really work properly
            val doorbound = randomSchem.bounds(door.first, doorDir)
            if (doorbound.outOfBounds()) return@filter false

            game.rooms.none { otherRoom ->
                if (otherRoom.number + 1 == number) return@none false
                isOverlapping(doorbound, otherRoom.schematic.bounds(otherRoom.position, otherRoom.direction))
            }
        }

        if (availableDoorPositions.isEmpty()) {
            Logger.info("Available doors was empty")

            val newSchemList = schemList.filter { it != randomSchem }.filter {
                val bounds = it.bounds(position, direction)
                game.rooms.none { otherRoom ->
                    if (otherRoom.number + 1 == number) return@none false
                    isOverlapping(bounds, otherRoom.schematic.bounds(otherRoom.position, otherRoom.direction))
                }
            }
            if (newSchemList.isEmpty()) {
                Logger.info("NO MORE ROOMS ARE AVAILABLE")
                return null
            }

            return applyRoom(newSchemList)
        }
        val randomDoor = availableDoorPositions.random()
        val randomDoorDir = Direction.valueOf(randomDoor.second.getProperty("facing").uppercase())

        val paintingPositions = mutableSetOf<Pair<Point, Direction>>()

        val batch = AbsoluteBlockBatch(BatchOption().setCalculateInverse(true))

        val blockList = mutableListOf<Region.Block>()

        val doubleChests = mutableMapOf<Point, Pair<Point, Block>>()

        randomSchem.schem.apply { x, y, z, block ->
            val rotatedBlock = applyRotationToBlock(position, x, y, z, block, direction, randomSchem.schem)

            blockList.add(Region.Block(rotatedBlock.first.asPos(), rotatedBlock.second.stateId()))

            // Initialize chests
            if (rotatedBlock.second.compare(Block.CHEST)) {

                val type = rotatedBlock.second.getProperty("type")
                if (type == "single") {
                    val handler = ChestHandler(game, rotatedBlock.first)
                    chests.add(rotatedBlock.first to handler)
                    batch.setBlock(rotatedBlock.first, rotatedBlock.second.withHandler(handler))
                } else { // left or right
                    if (doubleChests.containsKey(rotatedBlock.first)) {
                        val doubleChest = doubleChests[rotatedBlock.first]!!
                        val handler = ChestHandler(game, rotatedBlock.first)
                        batch.setBlock(rotatedBlock.first, rotatedBlock.second.withHandler(handler))
                        batch.setBlock(doubleChest.first, doubleChest.second.withHandler(handler))
                        chests.add(rotatedBlock.first to handler)
                        doubleChests.remove(rotatedBlock.first)
                    } else {
                        val newPos = rotatedBlock.first.add(
                            Direction.valueOf(rotatedBlock.second.getProperty("facing").uppercase()).rotate().rotate()
                                .rotate().offset()
                        )
                        doubleChests[newPos] = rotatedBlock
                    }
                }
                return@apply
            }

            // Signs (painting, lever)
            if (rotatedBlock.second.compare(Block.WARPED_WALL_SIGN)) {
                val direction = Direction.valueOf(rotatedBlock.second.getProperty("facing").uppercase())

                paintingPositions.add(rotatedBlock.first to direction)

                return@apply
            }

            if (lightBlockTypes.any { it.compare(rotatedBlock.second) }) {
                if (darkRoom) {
                    // Do not add to light blocks list, and do not place the block (return early)
                    return@apply
                } else {
                    lightBlocks.add(rotatedBlock)
                }
            }

            if (!instance.getBlock(rotatedBlock.first).compare(Block.AIR)) return@apply

            batch.setBlock(rotatedBlock.first, rotatedBlock.second)
        }

        closets = blockList.count { Block.fromStateId(it.stateId)!!.compare(Block.SPRUCE_DOOR) } / 4

        if (!keyRoom) keyRoom = randomSchem.name.endsWith("key")

        if (keyRoom) {
            val keyItemStack = ItemStack.builder(Material.TRIPWIRE_HOOK)
                .displayName(Component.text("Room Key ${number + 1}", NamedTextColor.GOLD).noItalic())
                .meta {
                    it.canPlaceOn(Block.IRON_DOOR)
                }
                .build()

            chests.random().second.inventory.addRandomly(keyItemStack)
        }

        // loop through other doors and remove them
        doorPositions.forEachIndexed { i, door ->
            val doorDir = Direction.valueOf(door.second.getProperty("facing").uppercase())
            if (i == doorPositions.indexOf(randomDoor)) return@forEachIndexed // ignore the picked door

            applyDoor(game, batch, doorSchem, door.first, doorDir, instance)
        }

        applyDoor(game, batch, doorSchem, randomDoor.first, randomDoorDir, instance)
        game.activeDoorPosition = randomDoor.first
        game.activeDoorDirection = randomDoorDir
        game.doorPositions.add(randomDoor.first)

        paintingPositions.forEach {
            val painting = Entity(EntityType.PAINTING)
            val meta = painting.entityMeta as PaintingMeta
            // Remove paintings that are above 3 blocks wide and 2 blocks high
            meta.motive = PaintingMeta.Motive.values().filter { it.width <= 3*16 && it.height <= 2*16 }.random()
            meta.direction = it.second

            painting.setNoGravity(true)

            when (it.second) {
                Direction.SOUTH, Direction.EAST -> {
                    println(it.first.add(1.5, 0.0, 1.5))
//                    painting.setInstance(instance, it.first.add(1.5, 0.0, 1.5))
                }
                Direction.NORTH, Direction.WEST -> {
                    println(it.first.add(1.5, 0.0, 1.5))
//                    painting.setInstance(instance, it.first.add(2.5, 0.0, 2.5))
                }
                else -> {}
            }

            entityIds.add(painting.entityId)

            batch.setBlock(it.first, Block.AIR)
        }

        val data: NBTCompound = SNBTParser(
            StringReader("{\"GlowingText\":1B,\"Color\":\"brown\",\"Text1\":\"{\\\"text\\\":\\\"\\\"}\"," +
                "\"Text2\":\"{\\\"text\\\":\\\"Room ${number + 1}\\\"}\",\"Text3\":\"{\\\"text\\\":\\\"\\\"}\",\"Text4\":\"{\\\"text\\\":\\\"\\\"}\"}")
        ).parse() as NBTCompound

        batch.setBlock(
            randomDoor.first.add(0.0, 2.0, 0.0).sub(randomDoorDir.offset()),
            Block.DARK_OAK_WALL_SIGN
                .withProperties(mapOf("facing" to randomDoorDir.opposite().name.lowercase()))
                .withHandler(SignHandler)
                .withNbt(data)
        )

        schematic = randomSchem

        game.players.forEach {
            it.respawnPoint = position.asPos()
        }

        val future = CompletableFuture<Void>()
        batch.apply(instance) {
            instance.scheduler().buildTask {
                instance.relight(position.asPos())
            }.delay(3, TimeUnit.SERVER_TICK).schedule()

            future.complete(null)
        }!!
        return future
    }

}