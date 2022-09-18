package dev.emortal.doors.lobby

import dev.emortal.doors.Main.Companion.doorsConfig
import dev.emortal.doors.game.DoorsGame
import dev.emortal.doors.lobby.Elevator.Companion.elevatorTag
import dev.emortal.doors.util.RoomBounds
import dev.emortal.immortal.config.GameOptions
import dev.emortal.immortal.game.GameManager
import dev.emortal.immortal.game.LobbyGame
import dev.emortal.immortal.util.*
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.sound.Sound.Emitter
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.event.inventory.InventoryPreClickEvent
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.event.player.PlayerResourcePackStatusEvent
import net.minestom.server.event.player.PlayerTickEvent
import net.minestom.server.instance.AnvilLoader
import net.minestom.server.instance.Instance
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.item.metadata.LeatherArmorMeta
import net.minestom.server.resourcepack.ResourcePack
import net.minestom.server.resourcepack.ResourcePackStatus
import world.cepi.kstom.Manager
import world.cepi.kstom.event.listenOnly
import java.awt.Color
import java.net.URL
import java.security.MessageDigest
import java.time.Duration
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors


class DoorsLobby(gameOptions: GameOptions) : LobbyGame(gameOptions) {


    override var spawnPosition = Pos(0.0, 65.0, 0.0)

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



        val leftDoors = listOf<Point>(
            Pos(8.0, 65.0, 14.0),
            Pos(8.0, 65.0, 19.0),
            Pos(8.0, 65.0, 24.0),
            Pos(8.0, 65.0, 29.0),
            Pos(8.0, 65.0, 34.0),
        )
        val rightDoors = leftDoors.map { it.withX(-9.0) }
    }

    val executor = Executors.newScheduledThreadPool(1)
    val musicTasks = ConcurrentHashMap<UUID, ExecutorRunnable>()
    lateinit var leftElevators: List<Elevator>
    lateinit var rightElevators: List<Elevator>

    override fun gameDestroyed() {
        executor.shutdownNow()
        musicTasks.clear()
    }

    override fun gameStarted() {
        val instance = instance.get() ?: return

        instance.setTag(GameManager.doNotAutoUnloadChunkTag, true)
        instance.enableAutoChunkLoad(false)

        object : ExecutorRunnable(repeat = Duration.ofMillis(100), executor = executor) {
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

        leftElevators = leftDoors.mapIndexed { i, it -> Elevator(this, instance, it, RoomBounds(it.add(4.0, 0.0, -2.0), it.add(0.0, 0.0, 2.0)), i) }
        rightElevators = rightDoors.mapIndexed { i, it -> Elevator(this, instance, it, RoomBounds(it.add(-3.0, 0.0, -2.0), it.add(0.0, 0.0, 2.0)), i + 5) }
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
                rightElevators[elevatorIndex - 5]
            } else {
                leftElevators[elevatorIndex]
            }

            elevator.removePlayer(player)
        }

        player.removeTag(elevatorTag)
        musicTasks[player.uuid]?.cancel()
        musicTasks.remove(player.uuid)
    }

    override fun registerEvents() {
        eventNode.listenOnly<PlayerResourcePackStatusEvent> {
            when (status) {
                ResourcePackStatus.SUCCESS -> {
                    player.sendActionBar(Component.text("Resource pack applied successfully"))

                    musicTasks[player.uuid] = object : ExecutorRunnable(delay = Duration.ofSeconds(3), repeat = Duration.ofMillis(213_300), executor = executor) {
                        override fun run() {
                            player.playSound(Sound.sound(Key.key("music.dawnofthedoors"), Sound.Source.MASTER, 0.4f, 1f), Emitter.self())
                        }
                    }
                }

                ResourcePackStatus.DECLINED -> {
                    player.kick(Component.text("The resource pack is required. You can ignore the prompt by allowing server resource packs."))
                }

                ResourcePackStatus.FAILED_DOWNLOAD -> {
                    player.kick(Component.text("The resource pack failed to download. Please contact a staff member.\ndiscord.gg/TZyuMSha96"))
                }

                else -> {}
            }
        }

        eventNode.cancel<InventoryPreClickEvent>()

        eventNode.listenOnly<PlayerBlockInteractEvent> {
            isCancelled = true
            isBlockingItemUse = true
        }

        eventNode.listenOnly<PlayerTickEvent> {
            if (player.hasTag(elevatorTag)) {
                if (player.position.blockX() <= 8 && player.position.blockX() >= -9) {
                    val elevatorIndex = player.getTag(elevatorTag)

                    val elevator = if (elevatorIndex >= 5) {
                        rightElevators[elevatorIndex - 5]
                    } else {
                        leftElevators[elevatorIndex]
                    }

                    elevator.removePlayer(player)
                }

                return@listenOnly
            }

            if (player.position.blockX() <= 8 && player.position.blockX() >= -9) return@listenOnly

            val leftRoomCollide = leftElevators.firstOrNull { RoomBounds.isInside(it.bounds, player.position, 0) }

            if (leftRoomCollide != null) {
                leftRoomCollide.addPlayer(player)

                return@listenOnly
            }

            val rightRoomCollide = rightElevators.firstOrNull { RoomBounds.isInside(it.bounds, player.position, 0) }

            if (rightRoomCollide != null) {
                rightRoomCollide.addPlayer(player)

                return@listenOnly
            }
        }
    }

    override fun instanceCreate(): Instance {
//        val dim = Manager.dimensionType.getDimension(NamespaceID.from("fullbrighttt"))!!
        val instance = Manager.instance.createInstanceContainer()
        instance.time = 18000
        instance.timeRate = 0
        instance.timeUpdate = null
        instance.setHasLighting(false)

        instance.chunkLoader = AnvilLoader("./roomslobby/")

        instance.enableAutoChunkLoad(true)
//        instance.setTag(GameManager.doNotAutoUnloadChunkTag, true)

        val range = 3
//        val latch = CountDownLatch(range + 1 * range + 1)
//
//        instance.loadChunk(0, 0)
//        for (x in -range..range) {
//            for (z in -range..range) {
//                instance.loadChunk(x, z).thenAccept {
//                    println("${it.chunkX} ${it.chunkZ}")
//                    latch.countDown()
//                }
//            }
//        }
//        latch.await(5, TimeUnit.SECONDS)

        return instance
    }

}