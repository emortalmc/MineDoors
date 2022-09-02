package dev.emortal.doors.game

import dev.emortal.doors.Achievements
import dev.emortal.doors.applyRotationToBlock
import dev.emortal.doors.block.SingleChestHandler
import dev.emortal.doors.damage.HideDamage
import dev.emortal.doors.pathfinding.RushPathfinding
import dev.emortal.doors.pathfinding.offset
import dev.emortal.doors.pathfinding.rotate
import dev.emortal.doors.pathfinding.yaw
import dev.emortal.doors.util.MultilineHologramAEC
import dev.emortal.doors.util.lerp
import dev.emortal.immortal.config.GameOptions
import dev.emortal.immortal.game.Game
import dev.hypera.scaffolding.instance.SchematicChunkLoader
import dev.hypera.scaffolding.schematic.impl.SpongeSchematic
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.sound.SoundStop
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.title.Title
import net.minestom.server.advancements.FrameType
import net.minestom.server.advancements.notifications.Notification
import net.minestom.server.advancements.notifications.NotificationCenter
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
import net.minestom.server.instance.Instance
import net.minestom.server.instance.batch.AbsoluteBlockBatch
import net.minestom.server.instance.block.Block
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
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
import world.cepi.kstom.Manager
import world.cepi.kstom.event.listenOnly
import world.cepi.kstom.util.asPos
import world.cepi.kstom.util.playSound
import java.nio.file.Path
import java.time.Duration
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.inputStream

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

    val generatingRoom = AtomicBoolean(false)

    val roomNum = AtomicInteger(0)

    val doorPositions = CopyOnWriteArrayList<Point>()
    val rooms = CopyOnWriteArrayList<Room>()
    var activeDoorDirection: Direction = Direction.NORTH
    var activeDoorPosition: Point = Vec(8.0, 0.0, -19.0)

    val hidingTaskMap = ConcurrentHashMap<UUID, Task>()
    lateinit var rushPathfinding: RushPathfinding

    var doorOpenSecondsLeft: Int = 30
    var doorTimerHologram = MultilineHologramAEC(mutableListOf(Component.text(doorOpenSecondsLeft, NamedTextColor.GOLD)))
    var doorTask: Task? = null

    override fun gameStarted() {
        val instance = instance.get() ?: return

        rushPathfinding = RushPathfinding(instance)

        instance.playSound(Sound.sound(Key.key("music.elevatorjam"), Sound.Source.MASTER, 0.6f, 1f), Sound.Emitter.self())

        doorTimerHologram.setInstance(Pos(0.5, 0.5, -0.8), instance)
        doorTask = Manager.scheduler.buildTask {
            instance.sendMessage(Component.text(doorOpenSecondsLeft))
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
        }.repeat(TaskSchedule.tick(20)).schedule()

    }

    override fun gameDestroyed() {

    }

    override fun playerJoin(player: Player) {
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
            if (message == "lift") {
                player.playSound(Sound.sound(Key.key("music.elevatorjam"), Sound.Source.MASTER, 0.8f, 1f), Sound.Emitter.self())
            }
            if (message == "deathmusic") {
                player.playSound(Sound.sound(Key.key("music.guidinglight"), Sound.Source.MASTER, 0.8f, 1f), Sound.Emitter.self())
            }

            if (message == "cheats") {
                if (player.username != "emortaldev") {
                    player.sendMessage("only hacks for emortal")
                    return@listenOnly
                }
                player.gameMode = GameMode.CREATIVE
                player.food = 20
            }

            if (message == "setup") {
                val msg = MiniMessage.miniMessage().deserialize("Press <light_purple><key:key.advancements><reset> to view your achievements.")
                player.sendMessage(msg)

                Achievements.create(player)
            }

            if (message == "achieve") {
                val notif = Notification(
                    Component.text("Buddy System", NamedTextColor.WHITE),
                    FrameType.TASK,
                    ItemStack.of(Material.RED_TULIP)
                )
                NotificationCenter.send(notif, player)
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
//            entity.addEffect(Potion(PotionEffect.INVISIBILITY, 1, Short.MAX_VALUE.toInt()))

//                val playerRoom = rooms.first {
//                    RoomBounds.isInside(it.schematic.bounds(it.position, it.direction), player.position)
//                }

//                val lights = playerRoom.lightBlocks
//
//                if (lights.isNotEmpty()) {
//                    lateinit var task: Task
//                    task = Manager.scheduler.buildTask(object : Runnable {
//                        var lightBreakIndex = 0
//                        var loopIndex = 0
//
//                        override fun run() {
//                            val index = lightBreakIndex % lights.size
//                            val light = lights[index]
//                            val prevLight = lights[if (index == 0) lights.size - 1 else index]
//
//                            instance.setBlock(light.first, Block.AIR)
//                            instance.setBlock(prevLight.first, prevLight.second)
//
//                            instance.playSound(Sound.sound(SoundEvent.ENTITY_EXPERIENCE_ORB_PICKUP, Sound.Source.MASTER, 1.5f, 2f), light.first)
//
//                            loopIndex++
//                            lightBreakIndex++
//
//                            if (loopIndex > 6) {
//                                task.cancel()
//                            }
//                        }
//                    }).repeat(TaskSchedule.tick(5)).schedule()
//                }

                val paths = mutableListOf<List<Point>>()

                val doorPoses = doorPositions.takeLast(maxLoadedRooms - 2)

                doorPoses.forEachIndexed { i, it ->
//                    println(it)

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

                entity.setInstance(instance, doorPositions.first()).thenRun {
                    val packet = EntitySoundEffectPacket(SoundEvent.ENTITY_ZOMBIE_DEATH.id(), Sound.Source.MASTER, entity.entityId, 100f, 1f)
                    instance.sendGroupedPacket(packet)

                    var doorIndex = 0

                    var pathIndex = 0
                    val roomsCopy = rooms.toMutableList()
                    val startingRoom = (roomNum.get() - roomsCopy.size).coerceAtLeast(0)

                    lateinit var task: Task
                    task = entity.scheduler().buildTask {

                        //val hasLineOfSight = RaycastUtil.raycastBlock(instance, entity.position, player.position.sub(entity.position).asVec().normalize(), 50.0)
                        //meta.isCustomNameVisible = hasLineOfSight == null

                        entity.teleport(paths[pathIndex].elementAt(doorIndex).asPos().add(0.5, 1.0, 0.5))

//                        instance.showParticle(
//                            Particle.particle(
//                                type = ParticleType.LARGE_SMOKE,
//                                count = 20,
//                                data = OffsetAndSpeed(2.0f, 2.0f, 2.0f, 0.2f)
//                            ),
//                            entity.position.add(0.0, 1.0, 0.0).asVec()
//                        )

                        doorIndex += 2

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
                            player.sendMessage(pathIndex.toString())
                        }
                        if (pathIndex >= paths.size) {
                            entity.remove()
                            generateNextRoom(instance)
                            task.cancel()
                        }
                    }.repeat(TaskSchedule.tick(1)).schedule()
                }
            }
        }

        var bellHealth = 10
        eventNode.listenOnly<PlayerBlockInteractEvent> {
            if (block.compare(Block.BELL) && bellHealth > 0) {
                instance.playSound(Sound.sound(SoundEvent.BLOCK_BELL_USE, Sound.Source.MASTER, bellHealth.toFloat() / 10f, 2f), blockPosition.add(0.5, 0.5, 0.5))
                instance.sendGroupedPacket(BlockActionPacket(blockPosition, 1, 2, block.stateId().toInt()))

                bellHealth--
                return@listenOnly
            }

            if (block.compare(Block.POLISHED_BLACKSTONE_BUTTON) && block.getProperty("powered") == "false") {
                instance.stopSound(SoundStop.named(Key.key("music.elevatorjam")))
                instance.playSound(Sound.sound(Key.key("music.elevatorjam.ending"), Sound.Source.MASTER, 0.5f, 1f), Sound.Emitter.self())

                instance.setBlock(blockPosition, block.withProperty("powered", "true"))

                doorOpenSecondsLeft = 5
                doorTimerHologram.setLine(0, Component.text(doorOpenSecondsLeft, NamedTextColor.GOLD))

                return@listenOnly
            }

            if (block.compare(Block.CHEST)) {
                val handler = block.handler() as? SingleChestHandler ?: return@listenOnly

                player.openInventory(handler.inventory)

                instance.playSound(
                    Sound.sound(SoundEvent.BLOCK_CHEST_OPEN, Sound.Source.BLOCK, 1f, 1f),
                    blockPosition.add(0.5, 0.5, 0.5)
                )
            }

            if (block.compare(Block.SPRUCE_TRAPDOOR)) {
                this.isCancelled = true
                this.isBlockingItemUse = true
            }
            if (block.compare(Block.SPRUCE_DOOR)) {
                this.isCancelled = true
                this.isBlockingItemUse = true

                val doorDirection = Direction.valueOf(block.getProperty("facing").uppercase())
                val rotatedDir = doorDirection.rotate()
                val yOffset = if (block.getProperty("half") == "upper") -1.0 else 0.0

                val newPos = Pos(blockPosition.add(0.5, yOffset, 0.5)
                    .sub(doorDirection.offset())
                    .let {
                        if (block.getProperty("hinge") == "left") it.add(rotatedDir.normalX().toDouble() * 0.5, 0.0, rotatedDir.normalZ().toDouble() * 0.5)
                        else it.sub(rotatedDir.normalX().toDouble() * 0.5, 0.0, rotatedDir.normalZ().toDouble() * 0.5)
                    }
                    , doorDirection.yaw(), 0f)

                fun stopHiding() {
                    player.sendActionBar(Component.empty())

                    player.removeTag(hidingTag)
                    player.getAttribute(Attribute.MOVEMENT_SPEED).baseValue = 0.1f

                    player.teleport(
                        Pos(blockPosition.add(0.5, yOffset, 0.5)
                            .add(doorDirection.offset())
                            .let {
                                if (block.getProperty("hinge") == "left") it.add(rotatedDir.normalX().toDouble() * 0.5, 0.0, rotatedDir.normalZ().toDouble() * 0.5)
                                else it.sub(rotatedDir.normalX().toDouble() * 0.5, 0.0, rotatedDir.normalZ().toDouble() * 0.5)
                            }
                            , doorDirection.yaw(), 0f)
                    )

                    instance.setBlock(newPos, Block.AIR)

                    hidingTaskMap[player.uuid]?.cancel()
                    hidingTaskMap.remove(player.uuid)
                }

                if (player.hasTag(hidingTag)) {
                    stopHiding()

                    return@listenOnly
                }

                if (instance.getBlock(newPos).compare(Block.STRUCTURE_VOID)) {
                    player.sendActionBar(Component.text("There is already someone hiding there", NamedTextColor.RED))
                    return@listenOnly
                }

                player.teleport(newPos)

                player.sendActionBar(
                    Component.text("You are hidden - Right click to get out")
                )

                instance.setBlock(newPos, Block.STRUCTURE_VOID)
                player.getAttribute(Attribute.MOVEMENT_SPEED).baseValue = 0f
                player.setTag(hidingTag, true)

                hidingTaskMap[player.uuid] = player.scheduler().buildTask(object : Runnable {
                    var iter = 0
                    var mod = 25

                    override fun run() {
                        if (mod <= 0 || iter % mod == 0) {
                            val titles = listOf("Get out", "get out", "Get out!", "Leave", "leave", "Leave!", "out", "Out", "Out!")

                            player.showTitle(
                                Title.title(
                                    Component.text(titles.random(), NamedTextColor.GRAY),
                                    Component.empty(),
                                    Title.Times.times(Duration.ofMillis(mod.coerceAtLeast(1) * 3L), Duration.ofMillis(50), Duration.ZERO)
                                )
                            )
                            player.playSound(Sound.sound(SoundEvent.BLOCK_FIRE_EXTINGUISH, Sound.Source.MASTER, 0.6f, 2f))

                            mod -= 2
                        }

                        if (mod <= -15) {
                            hidingTaskMap[player.uuid]?.cancel()
                            hidingTaskMap.remove(player.uuid)

                            val notif = Notification(
                                Component.text("And stay out!", NamedTextColor.WHITE),
                                FrameType.TASK,
                                ItemStack.of(Material.SPRUCE_DOOR)
                            )
                            NotificationCenter.send(notif, player)

                            player.damage(HideDamage(), 8f)

                            stopHiding()
                        }

                        iter++
                    }
                }).delay(Duration.ofSeconds(5)).repeat(TaskSchedule.tick(1)).schedule()
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

        generatingRoom.set(true)

        val newRoom = Room(this@DoorsGame, instance, activeDoorPosition, activeDoorDirection)
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

    override fun instanceCreate(): Instance {
        val dim = Manager.dimensionType.getDimension(NamespaceID.from("nolight"))!!
        val instance = Manager.instance.createInstanceContainer(dim)
        instance.time = 18000
        instance.timeRate = 0
        instance.timeUpdate = null

        val startingSchematic = SpongeSchematic()
        startingSchematic.read(Path.of("startingroom.schem").inputStream())

        instance.chunkLoader = SchematicChunkLoader.builder()
            .addSchematic(startingSchematic)
            .build()

        for (x in -3..3) {
            for (z in -3..3) {
                instance.loadChunk(x, z)
            }
        }

        return instance
    }


}