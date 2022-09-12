package dev.emortal.doors.lobby

import dev.emortal.doors.lobby.Elevator.Companion.elevatorTag
import dev.emortal.doors.util.RoomBounds
import dev.emortal.immortal.config.GameOptions
import dev.emortal.immortal.game.LobbyGame
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.title.Title
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.event.player.PlayerTickEvent
import net.minestom.server.instance.AnvilLoader
import net.minestom.server.instance.Instance
import world.cepi.kstom.Manager
import world.cepi.kstom.event.listenOnly
import java.time.Duration
import java.util.concurrent.Executors


class DoorsLobby(gameOptions: GameOptions) : LobbyGame(gameOptions) {

    override var spawnPosition = Pos(0.0, 65.0, 0.0)

    companion object {
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
    val leftElevators = leftDoors.mapIndexed { i, it -> Elevator(this, instance.get()!!, it, RoomBounds(it.add(4.0, 0.0, -2.0), it.add(0.0, 0.0, 2.0)), i) }
    val rightElevators = rightDoors.mapIndexed { i, it -> Elevator(this, instance.get()!!, it, RoomBounds(it.add(-3.0, 0.0, -2.0), it.add(0.0, 0.0, 2.0)), i + 5) }

    override fun gameDestroyed() {

    }

    override fun gameStarted() {
        val instance = instance.get() ?: return
    }


    override fun playerJoin(player: Player) {

        Manager.scheduler.buildTask {
            player.playSound(Sound.sound(Key.key("entity.rush.jumpscare"), Sound.Source.MASTER, 0.8f, 1f))
            player.showTitle(
                Title.title(
                    Component.text("\uE01A"),
                    Component.empty(),
                    Title.Times.times(Duration.ZERO, Duration.ofSeconds(1), Duration.ZERO)
                )
            )
        }.delay(Duration.ofSeconds(3)).schedule()
        Manager.scheduler.buildTask {
            player.playSound(Sound.sound(Key.key("entity.rush.jumpscare"), Sound.Source.MASTER, 0.8f, 1.2f))
            player.showTitle(
                Title.title(
                    Component.text("\uE01B"),
                    Component.empty(),
                    Title.Times.times(Duration.ZERO, Duration.ofSeconds(1), Duration.ZERO)
                )
            )
        }.delay(Duration.ofSeconds(4)).schedule()
        Manager.scheduler.buildTask {
            player.playSound(Sound.sound(Key.key("entity.rush.jumpscare"), Sound.Source.MASTER, 0.8f, 1.4f))

            player.showTitle(
                Title.title(
                    Component.text("\uE01C"),
                    Component.empty(),
                    Title.Times.times(Duration.ZERO, Duration.ofSeconds(1), Duration.ZERO)
                )
            )
        }.delay(Duration.ofSeconds(5)).schedule()

    }

    override fun playerLeave(player: Player) {
    }

    override fun registerEvents() {
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
        for (x in -3..3) {
            for (z in -3..3) {
                instance.loadChunk(x, z)
            }
        }

        return instance
    }

}