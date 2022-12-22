package dev.emortal.doors.lobby

import dev.emortal.doors.Main.Companion.doorsConfig
import dev.emortal.doors.game.DoorsGame
import dev.emortal.doors.lobby.Elevator.Companion.elevatorTag
import dev.emortal.doors.schematic.RoomBounds
import dev.emortal.immortal.game.Game
import dev.emortal.immortal.util.*
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.event.EventNode
import net.minestom.server.event.inventory.InventoryPreClickEvent
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.event.player.PlayerPacketEvent
import net.minestom.server.event.player.PlayerTickEvent
import net.minestom.server.event.trait.InstanceEvent
import net.minestom.server.instance.AnvilLoader
import net.minestom.server.instance.Chunk
import net.minestom.server.instance.Instance
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.item.metadata.LeatherArmorMeta
import net.minestom.server.network.packet.client.play.ClientSteerVehiclePacket
import net.minestom.server.resourcepack.ResourcePack
import world.cepi.kstom.Manager
import world.cepi.kstom.event.listenOnly
import java.awt.Color
import java.net.URL
import java.security.MessageDigest
import java.time.Duration
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet


class DoorsLobby : Game() {

    override val allowsSpectators = false
    override val countdownSeconds = 0
    override val maxPlayers = 50
    override val minPlayers = 0
    override val showScoreboard = false
    override val canJoinDuringGame = true
    override val showsJoinLeaveMessages = true

    val armourStandSeatList = CopyOnWriteArraySet<Point>()

    companion object {
        private const val url = "https://github.com/EmortalMC/Resourcepack/releases/download/latest/pack.zip"

        private val resourcePackPrompt = Component.text(
            "This resource pack is required.\nThis prompt won't appear again.",
            NamedTextColor.YELLOW
        )

        var resourcePack = ResourcePack.forced("https://github.com/EmortalMC/MineDoorsResourcePack/releases/latest/download/pack.zip", refreshSha1().decodeToString(), resourcePackPrompt)

        fun refreshSha1(): ByteArray {
            val digest = MessageDigest.getInstance("SHA-1")
            val fileInputStream = URL(url).openStream()
            var n = 0
            val buffer = ByteArray(8192)
            while (n != -1) {
                n = fileInputStream.read(buffer)
                if (n > 0)
                    digest.update(buffer, 0, n)
            }
            fileInputStream.close()
            return digest.digest()
        }


        val spawnPosition = Pos(0.0, 65.0, 0.0)

        val leftDoors = listOf<Point>(
            Pos(8.0, 65.0, 14.0),
            Pos(8.0, 65.0, 19.0),
            Pos(8.0, 65.0, 24.0),
            Pos(8.0, 65.0, 29.0),
            Pos(8.0, 65.0, 34.0),
        )
        val rightDoors = leftDoors.map { it.withX(-9.0) }
    }

    val musicTasks = ConcurrentHashMap<UUID, MinestomRunnable>()
    var leftElevators: List<Elevator>? = null
    var rightElevators: List<Elevator>? = null

    override fun getSpawnPosition(player: Player, spectator: Boolean): Pos = spawnPosition

    override fun gameEnded() {
        leftElevators = null
        rightElevators = null

        musicTasks.clear()
        armourStandSeatList.clear()
    }

    private val fullyLoadedFuture = CompletableFuture<Void>()

    override fun gameCreated() {
        start()

        fullyLoadedFuture.thenRun {
            leftElevators = leftDoors.mapIndexed { i, it -> Elevator(instance!!, it, RoomBounds(it.add(4.0, 0.0, -2.0), it.add(0.0, 0.0, 2.0)), i) }
            rightElevators = rightDoors.mapIndexed { i, it -> Elevator(instance!!, it, RoomBounds(it.add(-3.0, 0.0, -2.0), it.add(0.0, 0.0, 2.0)), i + 5) }
        }
    }

    override fun gameStarted() {

        object : MinestomRunnable(repeat = Duration.ofMillis(100), group = runnableGroup) {
            var rainbow = 0f

            override fun run() {
                rainbow += 0.015f
                if (rainbow >= 1f) {
                    rainbow = 0f
                }

                players.filter { it.team == DoorsGame.donatorTeam }.forEach { plr ->
                    plr.helmet = ItemStack.builder(Material.LEATHER_HELMET).meta(LeatherArmorMeta::class.java) {
                        it.color(net.minestom.server.color.Color(Color.HSBtoRGB(rainbow, 1f, 1f)))
                    }.build()
                }
            }
        }
    }


    override fun playerJoin(player: Player) {
        player.setResourcePack(resourcePack)

        if (doorsConfig.donators.contains(player.username)) {
            player.team = DoorsGame.donatorTeam
            player.sendMessage(Component.text("Thanks for being a donator! ❤", NamedTextColor.RED))
        } else {
            player.team = DoorsGame.team
        }

        val sepComponent = Component.text(" | ", NamedTextColor.DARK_GRAY)
        player.sendMessage(
            Component.text()
                .append(Component.text(centerSpaces("Welcome to Minecraft Doors", false) + "Welcome to ", TextColor.color(255, 150, 255)))
                .append(Component.text("Minecraft Doors", TextColor.color(255, 85, 255), TextDecoration.BOLD))
                .append(
                    Component.text("\n\nCredits", NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.runCommand("/credits"))
                )
                .append(sepComponent)
                .append(
                    Component.text("Discord", NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.openUrl("https://discord.gg/TZyuMSha96"))
                )
                .append(sepComponent)
                .append(
                    Component.text("Donate", NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.runCommand("/donate"))
                )
                .append(Component.text(" " + centerText("\n\nOriginal game created by LSplash Games"), TextColor.color(90, 90, 90)))
                .armify()
        )


    }

    override fun playerLeave(player: Player) {
        val elevatorIndex = player.getTag(elevatorTag)

        if (elevatorIndex != null) {
            val elevator = if (elevatorIndex >= 5) {
                rightElevators?.get(elevatorIndex - 5)
            } else {
                leftElevators?.get(elevatorIndex)
            }

            elevator?.removePlayer(player)
        }

        player.removeTag(elevatorTag)
        musicTasks[player.uuid]?.cancel()
        musicTasks.remove(player.uuid)
    }

    override fun registerEvents(eventNode: EventNode<InstanceEvent>) {
        eventNode.cancel<InventoryPreClickEvent>()

        eventNode.listenOnly<PlayerBlockInteractEvent> {
            isCancelled = true
            isBlockingItemUse = true

            if (block.name().contains("stair", true)) {
                if (player.vehicle != null) return@listenOnly
                if (armourStandSeatList.contains(blockPosition)) {
                    player.sendActionBar(Component.text("You can't sit on someone's lap", NamedTextColor.RED))
                    return@listenOnly
                }
                if (block.getProperty("half") == "top") return@listenOnly

                val armourStand = SeatEntity {
                    armourStandSeatList.remove(blockPosition)
                }

                val spawnPos = blockPosition.add(0.5, 0.3, 0.5)
                val yaw = when (block.getProperty("facing")) {
                    "east" -> 90f
                    "south" -> 180f
                    "west" -> -90f
                    else -> 0f
                }

                armourStand.setInstance(instance, Pos(spawnPos, yaw, 0f))
                    .thenRun {
                        armourStand.addPassenger(player)
                    }

                armourStandSeatList.add(blockPosition)
            }
        }

        eventNode.listenOnly<PlayerTickEvent> {
            if (player.hasTag(elevatorTag)) {
                if (player.position.blockX() <= 8 && player.position.blockX() >= -9) {
                    val elevatorIndex = player.getTag(elevatorTag)

                    val elevator = if (elevatorIndex >= 5) {
                        rightElevators?.get(elevatorIndex - 5)
                    } else {
                        leftElevators?.get(elevatorIndex)
                    }

                    elevator?.removePlayer(player)
                }

                return@listenOnly
            }

            if (player.position.blockX() <= 8 && player.position.blockX() >= -9) return@listenOnly

            val leftRoomCollide = leftElevators?.firstOrNull { RoomBounds.isInside(it.bounds, player.position, 0) }

            if (leftRoomCollide != null) {
                leftRoomCollide.addPlayer(this@DoorsLobby, player)

                return@listenOnly
            }

            val rightRoomCollide = rightElevators?.firstOrNull { RoomBounds.isInside(it.bounds, player.position, 0) }

            if (rightRoomCollide != null) {
                rightRoomCollide.addPlayer(this@DoorsLobby, player)

                return@listenOnly
            }
        }

        // Seats
        eventNode.listenOnly<PlayerPacketEvent> {
            if (packet is ClientSteerVehiclePacket) {
                val steerPacket = packet as ClientSteerVehiclePacket
                if (steerPacket.flags.toInt() == 2) {
                    if (player.vehicle != null && player.vehicle !is Player) {
                        val entity = player.vehicle!!
                        entity.removePassenger(player)
                    }
                    return@listenOnly
                }

            }
        }
    }

    override fun instanceCreate(): CompletableFuture<Instance> {
        val instanceFuture = CompletableFuture<Instance>()
//        val dim = Manager.dimensionType.getDimension(NamespaceID.from("fullbrighttt"))!!
        val newInstance = Manager.instance.createInstanceContainer()
        newInstance.time = 18000
        newInstance.timeRate = 0
        newInstance.timeUpdate = null
        newInstance.setHasLighting(false)

        newInstance.chunkLoader = AnvilLoader("./roomslobby/")

        newInstance.enableAutoChunkLoad(false)

        // 1 chunk required for player to spawn
        newInstance.loadChunk(0, 0).thenRun { instanceFuture.complete(newInstance) }

        val radius = 8
        val chunkFutures = mutableListOf<CompletableFuture<Chunk>>()
        for (x in -radius..radius) {
            for (z in -radius..radius) {
                val future= newInstance.loadChunk(x, z)
                chunkFutures.add(future)
                future.thenAccept { it.sendChunk() }
            }
        }

        CompletableFuture.allOf(*chunkFutures.toTypedArray()).thenRunAsync {
            fullyLoadedFuture.complete(null)
        }

        return instanceFuture
    }

}