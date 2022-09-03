package dev.emortal.doors.game

import dev.emortal.doors.Achievements
import dev.emortal.doors.applyRotationToBlock
import dev.emortal.doors.block.SingleChestHandler
import dev.emortal.doors.damage.HideDamage
import dev.emortal.doors.pathfinding.RushPathfinding
import dev.emortal.doors.pathfinding.offset
import dev.emortal.doors.pathfinding.yaw
import dev.emortal.doors.relight
import dev.emortal.doors.util.MultilineHologramAEC
import dev.emortal.doors.util.lerp
import dev.emortal.immortal.config.GameOptions
import dev.emortal.immortal.game.Game
import dev.hypera.scaffolding.schematic.impl.SpongeSchematic
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.sound.SoundStop
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.title.Title
import net.minestom.server.attribute.Attribute
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.entity.metadata.other.ArmorStandMeta
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.event.player.PlayerChatEvent
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.instance.Chunk
import net.minestom.server.instance.Instance
import net.minestom.server.instance.batch.AbsoluteBlockBatch
import net.minestom.server.instance.block.Block
import net.minestom.server.network.packet.server.play.BlockActionPacket
import net.minestom.server.network.packet.server.play.EntitySoundEffectPacket
import net.minestom.server.potion.Potion
import net.minestom.server.potion.PotionEffect
import net.minestom.server.sound.SoundEvent
import net.minestom.server.tag.Tag
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import net.minestom.server.utils.Direction
import net.minestom.server.utils.NamespaceID
import net.minestom.server.utils.time.TimeUnit
import world.cepi.kstom.Manager
import world.cepi.kstom.event.listenOnly
import world.cepi.kstom.util.asPos
import world.cepi.kstom.util.playSound
import java.nio.file.Path
import java.time.Duration
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.inputStream
import kotlin.math.floor

class DoorsGame(gameOptions: GameOptions) : Game(gameOptions) {

    companion object {
        val hidingTag = Tag.Boolean("hiding")

        const val maxLoadedRooms = 8

        fun applyDoor(game: DoorsGame, batch: AbsoluteBlockBatch, doorSchem: SpongeSchematic, doorPos: Point, direction: Direction, instance: Instance) {
            game.doorPositions.add(doorPos)

            doorSchem.apply { x, y, z, block ->
                val rotatedBlock = applyRotationToBlock(doorPos, x, y, z, block, direction, doorSchem)

                if (block.compare(Block.DARK_OAK_DOOR)) {
                    batch.setBlock(
                        rotatedBlock.first,
                        Block.IRON_DOOR.withProperties(rotatedBlock.second.properties())
                    )
                } else {
                    batch.setBlock(rotatedBlock.first, rotatedBlock.second)
                }
            }

            game.activeDoorPosition = doorPos
            game.activeDoorDirection = direction
        }
    }

    override var spawnPosition = Pos(0.5, 0.0, 0.5, 180f, 0f)



    val roomNum = AtomicInteger(-1)

    val doorPositions = CopyOnWriteArrayList<Point>()
    val rooms = CopyOnWriteArrayList<Room>()
    var activeDoorDirection: Direction = Direction.NORTH
    var activeDoorPosition: Point = Vec(8.0, 0.0, -19.0)

    private lateinit var rushPathfinding: RushPathfinding

    val generatingRoom = AtomicBoolean(false)

    private var doorOpenSecondsLeft: Int = 30
    private var doorTimerHologram = MultilineHologramAEC(mutableListOf(Component.text(doorOpenSecondsLeft, NamedTextColor.GOLD)))
    private var doorTask: Task? = null

    override fun gameStarted() {
        val instance = instance.get() ?: return

        rushPathfinding = RushPathfinding(instance)

        val startingSchematic = SpongeSchematic()
        startingSchematic.read(Path.of("startingroom.schem").inputStream())

        val futures = mutableListOf<CompletableFuture<Chunk>>()
        for (x in -3..3) {
            for (z in -3..3) {
                futures.add(instance.loadChunk(x, z))
            }
        }

        val lobbyRoom = Room(this, instance, spawnPosition, Direction.NORTH)

        CompletableFuture.allOf(*futures.toTypedArray()).join()
        lobbyRoom.applyRoom(listOf(startingSchematic))

        instance.scheduler().buildTask {
            instance.relight(spawnPosition)
        }.delay(30, TimeUnit.SERVER_TICK).schedule()

        doorTimerHologram.setInstance(Pos(0.5, 0.5, -0.8), instance)
        doorTask = instance.scheduler().buildTask {
            doorTimerHologram.setLine(0, Component.text(doorOpenSecondsLeft, NamedTextColor.GOLD))

            if (doorOpenSecondsLeft <= 0) {
                instance.setBlock(0, 0, -2, Block.IRON_DOOR.withProperties(mapOf("facing" to "north", "half" to "lower", "open" to "true")))
                instance.setBlock(0, 1, -2, Block.IRON_DOOR.withProperties(mapOf("facing" to "north", "half" to "upper", "open" to "true")))

                instance.playSound(Sound.sound(SoundEvent.BLOCK_IRON_DOOR_OPEN, Sound.Source.MASTER, 1f, 1f), Pos(0.5, 0.5, -2.0))

                doorTimerHologram.remove()
                doorTask?.cancel()
                doorTask = null
            }

            doorOpenSecondsLeft--
        }.repeat(20, TimeUnit.SERVER_TICK).schedule()

    }

    override fun gameDestroyed() {

    }

    override fun playerJoin(player: Player) {
        val msg = MiniMessage.miniMessage().deserialize("Press <light_purple><key:key.advancements><reset> to view your achievements.")
        player.sendMessage(msg)
        Achievements.create(player)

        if (doorTask != null) player.playSound(Sound.sound(Key.key("music.elevatorjam"), Sound.Source.MASTER, 0.6f, 1f), Sound.Emitter.self())

        player.food = 0
        player.foodSaturation = 0f
        player.addEffect(Potion(PotionEffect.JUMP_BOOST, 200.toByte(), Short.MAX_VALUE.toInt()))

        // Ambiance
        player.scheduler().buildTask {
            player.playSound(Sound.sound(SoundEvent.AMBIENT_BASALT_DELTAS_LOOP, Sound.Source.MASTER, 0.6f, 1f), Sound.Emitter.self())
        }.repeat(Duration.ofMillis(43150)).schedule()

        player.scheduler().buildTask {
            val sounds = listOf(
                SoundEvent.AMBIENT_CAVE,
                SoundEvent.AMBIENT_NETHER_WASTES_MOOD,
                SoundEvent.AMBIENT_BASALT_DELTAS_MOOD,
                SoundEvent.AMBIENT_BASALT_DELTAS_ADDITIONS,
                SoundEvent.AMBIENT_WARPED_FOREST_ADDITIONS
            )
            player.playSound(Sound.sound(sounds.random(), Sound.Source.MASTER, 0.4f, 1f), Sound.Emitter.self())
        }.delay(Duration.ofMinutes(2)).repeat(Duration.ofMinutes(2)).schedule()
    }

    override fun playerLeave(player: Player) {
    }

    override fun registerEvents() {
        eventNode.listenOnly<PlayerMoveEvent> {
            if (player.hasTag(hidingTag)) {
                isCancelled = true
            }
        }

        eventNode.listenOnly<PlayerChatEvent> {
            if (message == "cheats") {
                player.gameMode = GameMode.CREATIVE
                player.food = 20
            }

            if (message == "eyes") {
                val entity = Entity(EntityType.ARMOR_STAND)
                val meta = entity.entityMeta as ArmorStandMeta
                meta.setNotifyAboutChanges(false)
                //meta.radius = 0f
                meta.isSmall = true
                meta.isHasGlowingEffect = true
                meta.isHasNoBasePlate = true
                meta.isMarker = true
                meta.isInvisible = true
                meta.isCustomNameVisible = true
                meta.customName = Component.text("\uF80D\uE013\uF80D")
                meta.isHasNoGravity = true
                meta.setNotifyAboutChanges(true)

                entity.setInstance(instance, player.position.withY(1.0))
                instance.setBlock(player.position.withY(1.0), Block.LIGHT)

//            entity.scheduler().buildTask {
//                val distanceToEye = player.position.distance(entity.position)
//                val raycast = RaycastUtil.raycast(instance, player.position, player.position.sub(entity.position).asVec().normalize(), maxDistance = 60.0) { it != player }
//
//                println(raycast)
//
//                if (raycast.resultType == RaycastResultType.HIT_BLOCK) {
//                    println(instance.getBlock(raycast.hitPosition!!))
//                }
//
//                //meta.isCustomNameVisible = raycast == null
//            }.repeat(TaskSchedule.tick(1)).schedule()
            }

            if (message == "rush") {
                spawnRush(instance)
            }
        }

        var bellHealth = 10
        eventNode.listenOnly<PlayerBlockInteractEvent> {
            if (block.compare(Block.BELL) && bellHealth > 0) {
                instance.playSound(Sound.sound(SoundEvent.BLOCK_BELL_USE, Sound.Source.MASTER, bellHealth.toFloat() / 10f, 2f), blockPosition)
                instance.sendGroupedPacket(BlockActionPacket(blockPosition, 1, 2, block.stateId().toInt()))

                bellHealth--
                return@listenOnly
            }

            else if (block.compare(Block.POLISHED_BLACKSTONE_BUTTON) && block.getProperty("powered") == "false") {
                instance.stopSound(SoundStop.named(Key.key("music.elevatorjam")))
                instance.playSound(Sound.sound(Key.key("music.elevatorjam.ending"), Sound.Source.MASTER, 0.5f, 1f), Sound.Emitter.self())

                instance.setBlock(blockPosition, block.withProperty("powered", "true"))

                doorOpenSecondsLeft = 5
                doorTimerHologram.setLine(0, Component.text(doorOpenSecondsLeft, NamedTextColor.GOLD))

                return@listenOnly
            }

            else if (block.compare(Block.CHEST)) {
                val handler = block.handler() as? SingleChestHandler ?: return@listenOnly

                player.openInventory(handler.inventory)

                instance.playSound(
                    Sound.sound(SoundEvent.BLOCK_CHEST_OPEN, Sound.Source.BLOCK, 1f, 1f),
                    blockPosition.add(0.5, 0.5, 0.5)
                )
            }

            else if (block.compare(Block.SPRUCE_TRAPDOOR)) {
                this.isCancelled = true
                this.isBlockingItemUse = true

                val closet = Closet.getFromTrapdoor(block, blockPosition, instance) ?: return@listenOnly
                closet.handleInteraction(player, blockPosition, block, instance)
            }
            else if (block.compare(Block.SPRUCE_DOOR)) {
                this.isCancelled = true
                this.isBlockingItemUse = true

                val closet = Closet.getFromDoor(block, blockPosition)
                closet.handleInteraction(player, blockPosition, block, instance)
            }
        }

        eventNode.listenOnly<PlayerMoveEvent> {
            if (activeDoorPosition == Vec.ZERO) return@listenOnly
            if (generatingRoom.get()) {
                //player.sendMessage("ALREADY GENERATING!!!!")
                return@listenOnly
            }
            val distanceToDoor = player.position.distanceSquared(activeDoorPosition)

            if (distanceToDoor < 4.2 * 4.2) {
                generateNextRoom(instance)
            }
        }
    }

    private fun generateNextRoom(instance: Instance) {
        val oldActiveDoor = activeDoorPosition
        val newRoomEntryPos = activeDoorPosition.add(activeDoorDirection.offset())

        generatingRoom.set(true)

        val newRoom = Room(this@DoorsGame, instance, newRoomEntryPos, activeDoorDirection)
        val applyRoom = newRoom.applyRoom()

        if (applyRoom == null) {
            //player.sendMessage("No room")
            generatingRoom.set(false)
            roomNum.decrementAndGet()
            return
        }

        applyRoom.thenRun {
            val bottomBlock = instance.getBlock(oldActiveDoor)
            instance.setBlock(oldActiveDoor, bottomBlock.withProperty("open", "true"))

            val topBlock = instance.getBlock(oldActiveDoor.add(0.0, 1.0, 0.0))
            instance.setBlock(oldActiveDoor.add(0.0, 1.0, 0.0), topBlock.withProperties(mapOf("open" to "true", "half" to "upper")))

            instance.playSound(Sound.sound(Key.key("custom.door.open"), Sound.Source.MASTER, 0.8f, 1f), oldActiveDoor)
            generatingRoom.set(false)
        }

        rooms.add(newRoom)

        // Unload previous rooms
        if (rooms.size >= maxLoadedRooms) {
            val roomToRemove = rooms.removeAt(0)
            val nextRoom = rooms[1]

            doorPositions.remove(roomToRemove.position)

            roomToRemove.lightBlocks.clear()
            roomToRemove.chests.clear()
            roomToRemove.removalBatch?.apply(instance) {
                //player.sendMessage("next room: ${nextRoom.position}")
                instance.setBlock(nextRoom.position, Block.IRON_DOOR.withProperties(mapOf("facing" to nextRoom.direction.name.lowercase())))
                instance.setBlock(nextRoom.position.add(0.0, 1.0, 0.0), Block.IRON_DOOR.withProperties(mapOf("facing" to nextRoom.direction.name.lowercase(), "half" to "upper")))

            }
            roomToRemove.removalBatch = null
            roomToRemove.entityIds.forEach {
                Entity.getEntity(it)?.remove()
            }
            roomToRemove.entityIds.clear()


        }
    }

    fun spawnRush(instance: Instance) {
        val entity = Entity(EntityType.ARMOR_STAND)
        val meta = entity.entityMeta as ArmorStandMeta
        meta.setNotifyAboutChanges(false)
        //meta.radius = 0f
        meta.isSmall = true
        meta.isHasGlowingEffect = true
        meta.isHasNoBasePlate = true
        meta.isMarker = true
        meta.isInvisible = true
        meta.isCustomNameVisible = true
        meta.customName = Component.text("\uF80D\uE012\uF80D")
        meta.isHasNoGravity = true
        meta.setNotifyAboutChanges(true)

        val lights = rooms.last().lightBlocks.toMutableList()

        if (lights.isNotEmpty()) {
            lateinit var task: Task
            task = Manager.scheduler.buildTask(object : Runnable {
                var lightBreakIndex = 0
                var loopIndex = 0

                override fun run() {
                    val index = lightBreakIndex % lights.size
                    val light = lights[index]
                    val prevLight = lights[if (index == 0) lights.size - 1 else index - 1]

                    instance.setBlock(light.first, Block.AIR)
                    instance.setBlock(prevLight.first, prevLight.second)

                    instance.playSound(Sound.sound(SoundEvent.BLOCK_LEVER_CLICK, Sound.Source.MASTER, 1.5f, 2f), light.first)

                    loopIndex++
                    lightBreakIndex++

                    if (loopIndex > 24) {
                        task.cancel()
                    }
                }
            }).repeat(TaskSchedule.tick(1)).schedule()
        }

        val paths = mutableListOf<List<Point>>()

        val doorPoses = doorPositions.takeLast(maxLoadedRooms - 2)

        doorPoses.forEachIndexed { i, it ->
            if (i > 0) {
                val startPos = doorPoses[i - 1]

                val path = rushPathfinding.pathfind(startPos, it)
                if (path == null) {
                    val dist = startPos.distance(it).toInt()
                    val points = mutableListOf<Point>()

                    repeat(dist) { ind ->
                        points.add(startPos.lerp(it, ind.toDouble() / dist.toDouble()))
                    }

                    paths.add(points)
                } else {
                    paths.add(path.reversed())
                }
            }
        }

        entity.setInstance(instance, doorPoses.first()).thenRun {
            val packet = EntitySoundEffectPacket(SoundEvent.ENTITY_ZOMBIE_DEATH.id(), Sound.Source.MASTER, entity.entityId, 100f, 1f)
            instance.sendGroupedPacket(packet)

            var doorIndex = 0.0

            var pathIndex = 0
            val roomsCopy = rooms.toMutableList()

            lateinit var task: Task
            task = entity.scheduler().buildTask {
                if (doorIndex % 1 == 0.0) {
                    entity.teleport(paths[pathIndex][doorIndex.toInt()].asPos().add(0.5, 1.0, 0.5))

                } else {
                    val floored = floor(doorIndex).toInt()
                    val prev = paths[pathIndex][floored]
//                    val next = paths[pathIndex][(floored + 1).coerceAtMost(paths.size - 1)]
//                    val pos = prev.lerp(next, doorIndex - floored).asPos()
//                    entity.teleport(pos.add(0.5, 1.0, 0.5))
                    entity.teleport(prev.asPos().add(0.5, 1.0, 0.5))
                }


//                        instance.showParticle(
//                            Particle.particle(
//                                type = ParticleType.LARGE_SMOKE,
//                                count = 20,
//                                data = OffsetAndSpeed(2.0f, 2.0f, 2.0f, 0.2f)
//                            ),
//                            entity.position.add(0.0, 1.0, 0.0).asVec()
//                        )

                doorIndex += 1.5

                if (doorIndex >= paths[pathIndex].size) {
                    doorIndex -= paths[pathIndex].size
                    if (pathIndex + 1 < paths.size) {


                        var lightBreakIndex = 0
                        val room = roomsCopy[(pathIndex + 3).coerceIn(0, roomsCopy.size - 1)]
                        val lights = room.lightBlocks

                        if (lights.isNotEmpty()) {
                            lateinit var lightBreakTask: Task
                            lightBreakTask = Manager.scheduler.buildTask {
                                val rand = ThreadLocalRandom.current()

                                val light = lights[lightBreakIndex]
                                instance.setBlock(light.first, Block.AIR)
                                //player.sendMessage("Broke light")
                                instance.playSound(Sound.sound(SoundEvent.BLOCK_GLASS_BREAK, Sound.Source.MASTER, 3f, rand.nextFloat(0.5f, 0.8f)), light.first)

                                lightBreakIndex++
                                if (lightBreakIndex >= lights.size) {
                                    lightBreakTask.cancel()
                                    room.lightBlocks.clear()
                                }
                            }.repeat(TaskSchedule.tick(2)).schedule()
                        }
                    }

                    pathIndex++
                }
                if (pathIndex >= paths.size) {
                    entity.remove()
                    generateNextRoom(instance)
                    task.cancel()
                }
            }.repeat(1, TimeUnit.SERVER_TICK).schedule()
        }
    }

    override fun instanceCreate(): Instance {
        val dim = Manager.dimensionType.getDimension(NamespaceID.from("nolight"))!!
        val newInstance = Manager.instance.createInstanceContainer(dim)
        newInstance.time = 18000
        newInstance.timeRate = 0
        newInstance.timeUpdate = null

        return newInstance
    }


}