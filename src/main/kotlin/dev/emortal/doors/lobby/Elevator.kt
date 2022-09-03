package dev.emortal.doors.lobby

import dev.emortal.doors.game.DoorsGame
import dev.emortal.doors.util.RoomBounds
import dev.emortal.doors.util.MultilineHologramAEC
import dev.emortal.immortal.config.GameOptions
import dev.emortal.immortal.game.GameManager.joinGame
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance
import net.minestom.server.sound.SoundEvent
import net.minestom.server.tag.Tag
import net.minestom.server.timer.Task
import world.cepi.kstom.Manager
import world.cepi.kstom.util.asPos
import java.time.Duration
import java.util.concurrent.CopyOnWriteArraySet

data class Elevator(val instance: Instance, val doorPos: Point, val bounds: RoomBounds, val index: Int) {

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

    private var shorterSecondsLeft = 5
    private var timerSecondsLeft = 30
    private var timerTask: Task? = null

    fun startTimer() {
        timerTask = Manager.scheduler.buildTask {

            val secondsLeft = if (players.size == maxPlayers) {
                shorterSecondsLeft--
            } else {
                timerSecondsLeft--
            }

            if (secondsLeft <= 5) {
                players.forEach {
                    it.playSound(Sound.sound(SoundEvent.ENTITY_EXPERIENCE_ORB_PICKUP, Sound.Source.MASTER, 1f, 2f))
                }
            }

            if (secondsLeft <= 0) {
                val preparedGame = DoorsGame(GameOptions(
                    players.size,
                    players.size,
                    countdownSeconds = 0,
                    canJoinDuringGame = false,
                    showScoreboard = false,
                    showsJoinLeaveMessages = false,
                    allowsSpectators = true
                ))

                players.forEach {
                    it.joinGame(preparedGame, false, true)
                }

                players.clear()
                cancelTimer()

                return@buildTask
            }

            hologram.setLine(1, Component.text("${secondsLeft}s", NamedTextColor.GRAY))
        }.repeat(Duration.ofSeconds(1)).schedule()
    }

    fun cancelTimer() {
        timerTask?.cancel()
        timerTask = null

        shorterSecondsLeft = 5
        timerSecondsLeft = 30

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

        hologram.setInstance(doorPos.add(if (left) -0.5 else 1.5, 0.2, 0.5) as Pos, instance)
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

