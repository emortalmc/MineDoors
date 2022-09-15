package dev.emortal.doors.game

import dev.emortal.doors.applyRotationToBlock
import dev.emortal.doors.damage.DoorsEntity
import dev.emortal.doors.damage.EyesDamage
import dev.emortal.doors.damage.HideDamage
import dev.emortal.doors.damage.RushDamage
import dev.emortal.doors.pathfinding.RushPathfinding
import dev.emortal.doors.pathfinding.offset
import dev.emortal.doors.raycast.RaycastUtil
import dev.emortal.doors.relight
import dev.emortal.doors.util.ExecutorRunnable
import dev.emortal.doors.util.MultilineHologramAEC
import dev.emortal.doors.util.lerp
import dev.emortal.immortal.config.GameOptions
import dev.emortal.immortal.game.Game
import dev.emortal.immortal.game.Team
import dev.hypera.scaffolding.schematic.impl.SpongeSchematic
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.sound.SoundStop
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.title.Title
import net.minestom.server.attribute.Attribute
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.entity.metadata.other.AreaEffectCloudMeta
import net.minestom.server.entity.metadata.other.ArmorStandMeta
import net.minestom.server.event.entity.EntityDamageEvent
import net.minestom.server.event.inventory.InventoryCloseEvent
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.event.player.PlayerChatEvent
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.instance.Chunk
import net.minestom.server.instance.Instance
import net.minestom.server.instance.batch.AbsoluteBlockBatch
import net.minestom.server.instance.block.Block
import net.minestom.server.network.packet.server.play.BlockActionPacket
import net.minestom.server.network.packet.server.play.EntitySoundEffectPacket
import net.minestom.server.network.packet.server.play.TeamsPacket.NameTagVisibility
import net.minestom.server.potion.Potion
import net.minestom.server.potion.PotionEffect
import net.minestom.server.sound.SoundEvent
import net.minestom.server.tag.Tag
import net.minestom.server.utils.Direction
import net.minestom.server.utils.NamespaceID
import net.minestom.server.utils.time.TimeUnit
import world.cepi.kstom.Manager
import world.cepi.kstom.event.listenOnly
import world.cepi.kstom.util.asPos
import world.cepi.particle.Particle
import world.cepi.particle.ParticleType
import world.cepi.particle.data.OffsetAndSpeed
import world.cepi.particle.showParticle
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.inputStream
import kotlin.math.floor

class DoorsGame(gameOptions: GameOptions) : Game(gameOptions) {

    companion object {
        val team = Team("everyone", nameTagVisibility = NameTagVisibility.NEVER)

        val hidingTag = Tag.Boolean("hiding")

        const val doorRange = 3.8
        const val rushRange = 10.0
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

    // 0.0 - 1.0
    var rushChance = 0.0
    var eyesChance = 0.0

    val doorPositions = CopyOnWriteArrayList<Point>()
    val rooms = CopyOnWriteArrayList<Room>()
    var activeDoorDirection: Direction = Direction.NORTH
    var activeDoorPosition: Point = Vec(8.0, 0.0, -19.0)

    private lateinit var rushPathfinding: RushPathfinding

    private val generatingRoom = AtomicBoolean(false)

    private var doorOpenSecondsLeft = AtomicInteger(30)
    private var doorTimerHologram = MultilineHologramAEC(mutableListOf(Component.text(doorOpenSecondsLeft.get(), NamedTextColor.GOLD)))
    private var doorTask: ExecutorRunnable? = null

    val executor = Executors.newScheduledThreadPool(1)


    override fun gameStarted() {
        val instance = instance.get() ?: return

        rushPathfinding = RushPathfinding(instance)

        doorTimerHologram.setInstance(Pos(0.5, 0.5, -0.8), instance)

        doorTask = object : ExecutorRunnable(repeat = Duration.ofSeconds(1), executor = executor, iterations = 31) {
            override fun run() {
                val doorSecondsLeft = doorOpenSecondsLeft.get()

                doorTimerHologram.setLine(0, Component.text(doorSecondsLeft, NamedTextColor.GOLD))

                if (doorSecondsLeft == 5) {
                    instance.setBlock(1, 1, -1, Block.POLISHED_BLACKSTONE_BUTTON.withProperties(mapOf("powered" to "true", "facing" to "south")))
                }

                if (doorSecondsLeft <= 0) {
                    instance.setBlock(0, 0, -2, Block.IRON_DOOR.withProperties(mapOf("facing" to "north", "half" to "lower", "open" to "true")))
                    instance.setBlock(0, 1, -2, Block.IRON_DOOR.withProperties(mapOf("facing" to "north", "half" to "upper", "open" to "true")))

                    instance.playSound(Sound.sound(SoundEvent.BLOCK_IRON_DOOR_OPEN, Sound.Source.MASTER, 1f, 1f), Pos(0.5, 0.5, -2.0))

                    players.forEach {
                        it.food = 0
                        it.foodSaturation = 0f
                        it.addEffect(Potion(PotionEffect.JUMP_BOOST, 200.toByte(), Short.MAX_VALUE.toInt()))
                    }

                    doorTimerHologram.remove()
                    doorTask?.cancel()
                    doorTask = null
                }

                doorOpenSecondsLeft.decrementAndGet()
            }
        }

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
        rooms.add(lobbyRoom)

        instance.scheduler().buildTask {
            instance.relight(spawnPosition)
        }.delay(30, TimeUnit.SERVER_TICK).schedule()

    }

    override fun gameDestroyed() {

    }

    override fun playerJoin(player: Player) {
        team.add(player)

//        val msg = MiniMessage.miniMessage().deserialize("Press <light_purple><key:key.advancements><reset> to view your achievements.")
//        player.sendMessage(msg)
//        Achievements.create(player)

        if (doorTask != null) player.playSound(Sound.sound(Key.key("music.elevatorjam"), Sound.Source.MASTER, 0.6f, 1f), Sound.Emitter.self())

        player.food = 0
        player.foodSaturation = 0f
        player.addEffect(Potion(PotionEffect.JUMP_BOOST, 200.toByte(), Short.MAX_VALUE.toInt()))

//        player.addEffect(Potion(PotionEffect.NIGHT_VISION, 1.toByte(), Short.MAX_VALUE.toInt()))

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
//        Achievements.remove(player)
    }

    override fun registerEvents() {
        eventNode.listenOnly<PlayerMoveEvent> {
            val closestPlayerToDoor = players.minBy { it.position.distanceSquared(activeDoorPosition) }

            if (closestPlayerToDoor.uuid == player.uuid) return@listenOnly

            val distanceToPlayer = closestPlayerToDoor.getDistanceSquared(player)

            player.getAttribute(Attribute.MOVEMENT_SPEED).baseValue = 0.1f + if (distanceToPlayer > 25*25) 0.2f else 0f

            if (player.hasTag(hidingTag)) {
                isCancelled = true
            }
        }

        eventNode.listenOnly<PlayerChatEvent> {
//            if (message == "cheats") {
//                player.gameMode = GameMode.CREATIVE
//                player.food = 20
//            }

            if (message == "eyessss") {
                spawnEyes(instance)
                isCancelled = true
            }

            if (message == "rushhhh") {
                spawnRush(instance)
                isCancelled = true
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

                doorOpenSecondsLeft.set(5)
                doorTimerHologram.setLine(0, Component.text(5, NamedTextColor.GOLD))

                return@listenOnly
            }

            else if (block.compare(Block.CHEST)) {
                ChestLoot.openChest(player, block, blockPosition)
            }

            else if (block.compare(Block.SPRUCE_DOOR)) {
                this.isCancelled = true
                this.isBlockingItemUse = true

                val closet = Closet.getFromDoor(this@DoorsGame, block, blockPosition)
                closet.handleInteraction(player, blockPosition, block, instance)
            }

            else if (block.compare(Block.OAK_DOOR)) {

            }

            else {
                isCancelled = true
                isBlockingItemUse = true
            }
        }

        eventNode.listenOnly<InventoryCloseEvent> {
            ChestLoot.freeChest(player)
        }

        eventNode.listenOnly<EntityDamageEvent> {
            val player = entity as? Player ?: return@listenOnly
            if (player.gameMode != GameMode.ADVENTURE) return@listenOnly

            // If the player would die from this damage
            if (player.health - damage <= 0f) {
                isCancelled = true // save them :sunglassses:

                val doorsEntity = when (damageType) {
                    is EyesDamage -> DoorsEntity.EYES
                    is HideDamage -> DoorsEntity.HIDE
                    is RushDamage -> DoorsEntity.RUSH
                    else -> return@listenOnly
                }

                kill(player, doorsEntity) // jk
            }
        }

        eventNode.listenOnly<PlayerMoveEvent> {
            if (activeDoorPosition == Vec.ZERO) return@listenOnly
            if (player.gameMode != GameMode.ADVENTURE) return@listenOnly
            if (generatingRoom.get()) {
                //player.sendMessage("ALREADY GENERATING!!!!")
                return@listenOnly
            }
            val distanceToDoor = player.position.distanceSquared(activeDoorPosition)

            if (distanceToDoor < doorRange * doorRange) {
                generateNextRoom(instance)
            }
        }
    }

    private fun kill(player: Player, killer: DoorsEntity) {
//        sendMessage(
//            Component.text("")
//        )

        player.gameMode = GameMode.SPECTATOR
        player.isInvisible = true

        fun deathAnimation() {
            player.playSound(Sound.sound(Key.key("custom.death"), Sound.Source.MASTER, 0.6f, 1f), Sound.Emitter.self())
            player.sendActionBar(Component.text("\uE00A"))

            player.showTitle(
                Title.title(
                    Component.text("\uE019", TextColor.color(255, 200, 200)),
                    Component.empty(),
                    Title.Times.times(Duration.ofMillis(0), Duration.ofMillis(1500), Duration.ofMillis(500))
                )
            )

            val bgCharacters = listOf('\uE015', '\uE016', '\uE017', '\uE018')

            val messages = listOf(killer.messages.first().first) + killer.messages.first().second.split(". ").map { if (it.endsWith(".")) it else "${it}." }

            val ticksPerMessage = (20 * 6.5).toInt()

            object : ExecutorRunnable(delay = Duration.ofSeconds(2), repeat = Duration.ofMillis(50), iterations = ticksPerMessage * messages.size, executor = executor) {

                override fun run() {
                    val currentIter = currentIteration.get()

                    if (currentIter == 0) {
                        player.playSound(Sound.sound(Key.key("music.guidinglight"), Sound.Source.MASTER, 0.6f, 1f), Sound.Emitter.self())
                    }
                    if (currentIter % ticksPerMessage == 0) {
                        val currentMessage = messages[(currentIter.toDouble() / 80.0).toInt()]

                        player.showTitle(
                            Title.title(
                                Component.empty(),
                                Component.text(currentMessage, TextColor.color(183, 245, 245)),
                                Title.Times.times(Duration.ofMillis(300), Duration.ofMillis((ticksPerMessage * 50L) - 600L), Duration.ofMillis(300))
                            )
                        )
                    }

                    player.sendActionBar(Component.text(bgCharacters[currentIter % bgCharacters.size]))
                }

                override fun cancelled() {
                    player.stopSound(SoundStop.named(Key.key("music.guidinglight")))
                    player.playSound(Sound.sound(Key.key("music.guidinglight.ending"), Sound.Source.MASTER, 0.6f, 1f), Sound.Emitter.self())

                    val playerToSpectate = players.filter { it != player }.randomOrNull()
                    if (playerToSpectate != null) player.spectate(playerToSpectate)
                }
            }
        }

        if (killer == DoorsEntity.RUSH) {
            val firstTitle = 10
            val secondTitle = ThreadLocalRandom.current().nextInt(30, 45)
            val jumpscare = ThreadLocalRandom.current().nextInt(80, 110)

            object : ExecutorRunnable(repeat = Duration.ofMillis(50), executor = executor, iterations = jumpscare + 24) {
                override fun run() {
                    val currentIter = currentIteration.get()

                    player.sendActionBar(Component.text("\uE00A"))

                    if (currentIter == firstTitle || currentIter == secondTitle) {
                        player.playSound(Sound.sound(Key.key("entity.rush.jumpscare"), Sound.Source.MASTER, 0.8f, if (currentIter == firstTitle) 1f else 1.2f))
                        player.showTitle(
                            Title.title(
                                Component.text(if (currentIter == firstTitle) "\uE01A" else "\uE01B"),
                                Component.empty(),
                                Title.Times.times(Duration.ZERO, Duration.ofSeconds(4), Duration.ZERO)
                            )
                        )
                    }

                    if (currentIter == jumpscare) {
                        player.stopSound(SoundStop.named(Key.key("entity.rush.jumpscare")))
                        player.playSound(Sound.sound(Key.key("entity.rush.death"), Sound.Source.MASTER, 0.8f, 1f))

                        player.showTitle(
                            Title.title(
                                Component.text("\uE01C"),
                                Component.empty(),
                                Title.Times.times(Duration.ZERO, Duration.ofMillis(1200), Duration.ZERO)
                            )
                        )
                    }
                }

                override fun cancelled() {
                    player.clearTitle()
                    deathAnimation()
                }
            }
        } else {
            deathAnimation()
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

        // Only begin allowing entity spawns above or on room 5
        if (roomNum.get() >= 5) {
            rushChance += 0.07 // 7% per room
            eyesChance += 0.003 // 0.3% per room

            // Roll
            val random = ThreadLocalRandom.current()

            if (random.nextDouble() < rushChance) {
                // If room does not have atleast 2 closets
                if (newRoom.closets < 2) {
                    // guarantee rush for next available room
                    rushChance = 1.0
                } else {
                    spawnRush(instance)
                    rushChance = 0.0
                }
            }
            if (random.nextDouble() < eyesChance) {
                spawnEyes(instance)
                eyesChance = 0.0
            }

            // 10% chance for a fake flicker
            if (random.nextDouble() < 0.10) {
                flickerLights(instance)
            }
        }
    }

    fun flickerLights(instance: Instance) {
        val lights = rooms.last().lightBlocks.toMutableList()

        if (lights.isNotEmpty()) {
            object : ExecutorRunnable(repeat = Duration.ofMillis(200), iterations = 8, executor = executor) {
                var lightBreakIndex = 0
                var loopIndex = 0

                override fun run() {
                    val index = lightBreakIndex % lights.size
                    val light = lights[index]
                    val prevLight = lights[if (index == 0) lights.size - 1 else index - 1]

                    instance.setBlock(light.first, Block.AIR)
                    instance.setBlock(prevLight.first, prevLight.second)

                    instance.playSound(Sound.sound(Key.key("custom.light.zap"), Sound.Source.MASTER, 2f, 1f), light.first)

                    loopIndex++
                    lightBreakIndex++
                }
            }
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

        flickerLights(instance)

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

        entity.isGlowing = true
        entity.updateViewableRule { player ->
            val playerPos = player.position.add(0.0, 1.6, 0.0)
            val distanceToEye = playerPos.distance(entity.position)
            val directionToEye = playerPos.sub(entity.position).asVec().normalize()
            val raycast = RaycastUtil.raycastBlock(instance, entity.position, directionToEye, maxDistance = distanceToEye)

            raycast == null
        }

        entity.setInstance(instance, doorPoses.first()).thenRun {
            val packet = EntitySoundEffectPacket(SoundEvent.ENTITY_ZOMBIE_DEATH.id(), Sound.Source.MASTER, entity.entityId, 100f, 1f, 0)
            instance.sendGroupedPacket(packet)

            var doorIndex = 0.0

            var pathIndex = 0
            val roomsCopy = rooms.toMutableList()

            object : ExecutorRunnable(repeat = Duration.ofMillis(50), executor = executor) {
                override fun run() {
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


                    instance.showParticle(
                        Particle.particle(
                            type = ParticleType.LARGE_SMOKE,
                            count = 20,
                            data = OffsetAndSpeed(2.0f, 2.0f, 2.0f, 0.2f)
                        ),
                        entity.position.add(0.0, 1.0, 0.0).asVec()
                    )

                    // Get players that are inside of rush's range and are not hiding in a closet
                    instance.players.filter { !it.hasTag(hidingTag) && it.getDistanceSquared(entity) < rushRange * rushRange }.forEach {
                        it.damage(RushDamage(entity), 420f)
                    }

                    entity.updateViewableRule()

                    doorIndex += 1

                    if (doorIndex >= paths[pathIndex].size) {
                        doorIndex -= paths[pathIndex].size
                        if (pathIndex + 1 < paths.size) {

                            var lightBreakIndex = 0
                            val room = roomsCopy[(pathIndex + 3).coerceIn(0, roomsCopy.size - 1)]
                            val lights = room.lightBlocks

                            if (lights.isNotEmpty()) {
                                lights.iterator().forEachRemaining {
                                    instance.setBlock(it.first, Block.AIR)
                                    if (it.second.compare(Block.LIGHT)) lights.remove(it)
                                }

                                object : ExecutorRunnable(repeat = Duration.ofMillis(100), executor = executor) {
                                    override fun run() {
                                        if (lightBreakIndex >= lights.size) return

                                        val light = lights[lightBreakIndex]

                                        //player.sendMessage("Broke light")
                                        instance.playSound(Sound.sound(Key.key("custom.light.break"), Sound.Source.MASTER, 0.5f, 1f), light.first)

                                        lightBreakIndex++
                                        if (lightBreakIndex >= lights.size) {
                                            cancel()
                                            room.lightBlocks.clear()
                                        }
                                    }
                                }
                            }
                        }

                        pathIndex++
                    }
                    if (pathIndex >= paths.size) {
                        entity.remove()
                        generateNextRoom(instance)
                        cancel()
                    }
                }
            }
        }
    }

    fun spawnEyes(instance: Instance) {
        val entity = Entity(EntityType.AREA_EFFECT_CLOUD)
        val meta = entity.entityMeta as AreaEffectCloudMeta
//                meta.setNotifyAboutChanges(false)
        meta.radius = 0f
//                meta.isSmall = true
//                meta.isHasGlowingEffect = true
//                meta.isHasNoBasePlate = true
//                meta.isMarker = true
//                meta.isInvisible = true
        meta.isCustomNameVisible = true
        meta.customName = Component.text("\uF80D\uE013\uF80D")
        meta.isHasNoGravity = true
        meta.setNotifyAboutChanges(true)

        val roomNumberOnSpawn = roomNum.get()

        val lastRoom = rooms.last()
        val lastRoomBounds = lastRoom.schematic.bounds(lastRoom.position, lastRoom.direction)

        var startPos = lastRoomBounds.bottomRight.lerp(lastRoomBounds.topLeft, 0.5).asPosition()

        var firstBlockY = lastRoomBounds.bottomRight.y()
        var airBlockY = 0.0
        var eyesSpawnPosition: Pos? = null
        stuff@ while (eyesSpawnPosition == null) {
            if (!instance.getBlock(startPos.withY(firstBlockY)).isAir) {
                airBlockY = firstBlockY + 1.0
                while (!instance.getBlock(startPos.withY(airBlockY)).isAir) {
                    airBlockY++
                }
                eyesSpawnPosition = startPos.withY(airBlockY + 1.0)
            }

            firstBlockY++
        }

        entity.setInstance(instance, eyesSpawnPosition)

        instance.setBlock(eyesSpawnPosition, Block.LIGHT)

        instance.playSound(Sound.sound(Key.key("entity.eyes.initiate"), Sound.Source.MASTER, 1f, 1f), eyesSpawnPosition)

        entity.scheduler().buildTask {
            instance.playSound(Sound.sound(Key.key("entity.eyes.ambiance"), Sound.Source.MASTER, 1f, 1f), eyesSpawnPosition)
        }.repeat(110, TimeUnit.SERVER_TICK).schedule()

        entity.scheduler().buildTask {

            if (roomNumberOnSpawn != roomNum.get()) {
                instance.stopSound(SoundStop.named(Key.key("entity.eyes.ambiance")))
                instance.stopSound(SoundStop.named(Key.key("entity.eyes.initiate")))
                entity.remove()
            }

            val random = ThreadLocalRandom.current()
            val jitter = 0.03

            entity.teleport(eyesSpawnPosition.add(random.nextDouble(-jitter, jitter), random.nextDouble(-jitter, jitter), random.nextDouble(-jitter, jitter)))

            entity.updateViewableRule { player ->
                val playerPos = player.position.add(0.0, 1.6, 0.0)
                val distanceToEye = playerPos.distance(eyesSpawnPosition)
                val directionToEye = playerPos.sub(eyesSpawnPosition).asVec().normalize()
                val raycast = RaycastUtil.raycastBlock(instance, entity.position, directionToEye, maxDistance = distanceToEye)

                raycast == null
            }

            // Don't deal damage for 2.5 seconds
            if (entity.aliveTicks > 50) players.forEach {
                if (it.gameMode != GameMode.ADVENTURE) return@forEach
                if (!entity.viewers.contains(it)) return@forEach

                val dir = Pos.ZERO.withDirection(eyesSpawnPosition.add(0.0, 1.0, 0.0).sub(it.position).asVec().normalize())
                val yawDiff = dir.yaw - it.position.yaw
                val pitchDiff = dir.pitch - it.position.pitch

                if (yawDiff > -70 && yawDiff < 70 && pitchDiff > -55 && pitchDiff < 46) {
                    if (it.aliveTicks % 4L == 0L) {
                        it.playSound(Sound.sound(Key.key("entity.eyes.attack"), Sound.Source.MASTER, 1f, 1f))
                        it.damage(EyesDamage(entity), 2f)
                    }
                }
            }
        }.repeat(1, TimeUnit.SERVER_TICK).schedule()
    }

    override fun instanceCreate(): Instance {
//        val dim = Manager.dimensionType.getDimension(NamespaceID.from("nolight"))!!
        val dim = Manager.dimensionType.getDimension(NamespaceID.from("fullbrighttt"))!!
        val newInstance = Manager.instance.createInstanceContainer(dim)
        newInstance.time = 18000
        newInstance.timeRate = 0
        newInstance.timeUpdate = null

        return newInstance
    }


}