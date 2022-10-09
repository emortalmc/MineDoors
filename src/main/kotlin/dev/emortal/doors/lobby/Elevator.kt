package dev.emortal.doors.lobby

import dev.emortal.doors.game.DoorsGame
import dev.emortal.doors.util.MultilineHologramAEC
import dev.emortal.doors.schematic.RoomBounds
import dev.emortal.immortal.config.GameOptions
import dev.emortal.immortal.game.GameManager.joinGame
import dev.emortal.immortal.util.ExecutorRunnable
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.sound.SoundStop
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance
import net.minestom.server.sound.SoundEvent
import net.minestom.server.tag.Tag
import world.cepi.kstom.util.asPos
import java.time.Duration
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicInteger

data class Elevator(val game: DoorsLobby, val instance: Instance, val doorPos: Point, val bounds: RoomBounds, val index: Int) {

    companion object {
        val elevatorTag = Tag.Integer("elevatorNum")
    }

    private val players = CopyOnWriteArraySet<Player>()
    private val left = doorPos.blockX() > 0
    val maxPlayers = if (left) {
        (index + 1).coerceAtMost(4)
    } else {
        (index - 4).coerceAtMost(4)
    }

    private var shorterSecondsLeft = AtomicInteger(5)
    private var timerSecondsLeft = AtomicInteger(30)
    private var timerTask: ExecutorRunnable? = null

    fun startTimer() {
        timerTask = object : ExecutorRunnable(repeat = Duration.ofSeconds(1), executor = game.executor) {

            override fun run() {
                val secondsLeft = if (players.size == maxPlayers) {
                    shorterSecondsLeft.decrementAndGet()
                } else {
                    timerSecondsLeft.decrementAndGet()
                }

                if (secondsLeft <= 5) {
                    players.forEach {
                        it.playSound(Sound.sound(SoundEvent.ENTITY_EXPERIENCE_ORB_PICKUP, Sound.Source.MASTER, 1f, 2f))
                    }
                }

                if (secondsLeft < 0) {
                    hologram.setLine(1, Component.text("Joining", NamedTextColor.GRAY))

                    players.forEach {
                        it.stopSound(SoundStop.named(Key.key("music.dawnofthedoors")))
                        it.playSound(Sound.sound(Key.key("music.dawnofthedoors.ending"), Sound.Source.MASTER, 1f, 1f), Sound.Emitter.self())
                        game.musicTasks[it.uuid]?.cancel()
                        game.musicTasks.remove(it.uuid)
                    }

                    val preparedGame = DoorsGame(GameOptions(
                        players.size,
                        players.size,
                        countdownSeconds = 0,
                        canJoinDuringGame = false,
                        showScoreboard = false,
                        showsJoinLeaveMessages = false,
                        allowsSpectators = true
                    ))

                    preparedGame.readyFuture.thenRun {
                        if (players.size != preparedGame.gameOptions.minPlayers) {
                            preparedGame.gameOptions = preparedGame.gameOptions.copy(minPlayers = players.size, maxPlayers = players.size)
                        }
                        players.forEach {
                            it.joinGame(preparedGame, false, true)
                        }

                        players.clear()
                        cancelTimer()
                    }

                    return
                }

                hologram.setLine(1, Component.text("${secondsLeft + 1}s", NamedTextColor.GRAY))
            }

        }
    }

    fun cancelTimer() {
        timerTask?.cancel()
        timerTask = null

        shorterSecondsLeft.set(5)
        timerSecondsLeft.set(30)

        refreshHologram()
        hologram.setLine(1, Component.empty())
    }

    fun addPlayer(player: Player) {
        if (players.size >= maxPlayers) {
            player.teleport(doorPos.asPos())
            if (left) {
                player.velocity = Vec(-15.0, 5.0, 0.0)
            } else {
                player.velocity = Vec(15.0, 5.0, 0.0)
            }

            player.playSound(Sound.sound(SoundEvent.ENTITY_VILLAGER_NO, Sound.Source.MASTER, 1f, 1f), Sound.Emitter.self())
            player.sendActionBar(Component.text("That elevator is full", NamedTextColor.RED))
            return
        }

        players.add(player)

        if (timerTask == null) {
            startTimer()
        }

        players.forEach {
            it.playSound(Sound.sound(SoundEvent.ENTITY_ITEM_FRAME_ADD_ITEM, Sound.Source.MASTER, 1f, 1f))
        }

        player.setTag(elevatorTag, index)
        refreshHologram()
    }

    fun removePlayer(player: Player) {
        players.forEach {
            it.playSound(Sound.sound(SoundEvent.ENTITY_ITEM_FRAME_REMOVE_ITEM, Sound.Source.MASTER, 1f, 1f))
        }

        players.remove(player)

        if (players.isEmpty()) {
            cancelTimer()
        }

        player.removeTag(elevatorTag)
        refreshHologram()
    }

    val hologram = MultilineHologramAEC(
        mutableListOf(
            Component.text()
                .append(Component.text("0"))
                .append(Component.text("/", NamedTextColor.GRAY))
                .append(Component.text(maxPlayers))
                .build(),
            Component.empty()
        )
    )

    init {

        hologram.setInstance(doorPos.add(if (left) -0.5 else 1.5, -0.5, 0.5) as Pos, instance)
    }

    private fun refreshHologram() {
        hologram.setLine(
            0,
            Component.text()
                .append(Component.text(players.size))
                .append(Component.text("/", NamedTextColor.GRAY))
                .append(Component.text(maxPlayers))
                .build()
        )
    }

}

