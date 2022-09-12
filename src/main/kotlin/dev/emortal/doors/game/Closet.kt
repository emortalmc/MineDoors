package dev.emortal.doors.game

import dev.emortal.doors.damage.HideDamage
import dev.emortal.doors.pathfinding.offset
import dev.emortal.doors.pathfinding.rotate
import dev.emortal.doors.pathfinding.yaw
import dev.emortal.doors.util.ExecutorRunnable
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.sound.SoundStop
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.title.Title
import net.minestom.server.attribute.Attribute
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.tag.Tag
import net.minestom.server.utils.Direction
import java.time.Duration
import java.util.*
import java.util.concurrent.ConcurrentHashMap

data class Closet(val game: DoorsGame, val doorDirection: Direction, val rotatedDir: Direction, val yOffset: Double, val pos: Pos) {
    companion object {
        val hidingTaskMap = ConcurrentHashMap<UUID, ExecutorRunnable>()
        val canLeaveTag = Tag.Boolean("canLeaveCloset")

        fun getFromDoor(game: DoorsGame, block: Block, blockPosition: Point): Closet {
            val doorDirection = Direction.valueOf(block.getProperty("facing").uppercase())
            val rotatedDir = doorDirection.rotate()
            val yOffset = if (block.getProperty("half") == "upper") -1.0 else 0.0

            return Closet(game, doorDirection, rotatedDir, yOffset, Pos(blockPosition.add(0.5, yOffset, 0.5)
                .sub(doorDirection.offset())
                .let {
                    if (block.getProperty("hinge") == "left") it.add(rotatedDir.normalX().toDouble() * 0.5, 0.0, rotatedDir.normalZ().toDouble() * 0.5)
                    else it.sub(rotatedDir.normalX().toDouble() * 0.5, 0.0, rotatedDir.normalZ().toDouble() * 0.5)
                },
                doorDirection.yaw(), 0f))
        }
    }

    fun stopHiding(player: Player, blockPosition: Point, block: Block, instance: Instance) {
        player.sendActionBar(Component.empty())

        player.removeTag(DoorsGame.hidingTag)
        player.getAttribute(Attribute.MOVEMENT_SPEED).baseValue = 0.1f

        player.stopSound(SoundStop.named(Key.key("entity.hide")))

        player.teleport(
            Pos(blockPosition.add(0.5, yOffset, 0.5)
                .add(doorDirection.offset())
                .let {
                    if (block.getProperty("hinge") == "left") it.add(rotatedDir.normalX().toDouble() * 0.5, 0.0, rotatedDir.normalZ().toDouble() * 0.5)
                    else it.sub(rotatedDir.normalX().toDouble() * 0.5, 0.0, rotatedDir.normalZ().toDouble() * 0.5)
                }
                , doorDirection.yaw(), 0f)
        )

        instance.setBlock(pos, Block.AIR)

        hidingTaskMap[player.uuid]?.cancel()
        hidingTaskMap.remove(player.uuid)

        player.removeTag(canLeaveTag)
    }

    fun handleInteraction(player: Player, blockPosition: Point, block: Block, instance: Instance) {
        if (player.hasTag(DoorsGame.hidingTag)) {
            if(player.hasTag(canLeaveTag)) stopHiding(player, blockPosition, block, instance)
        } else if (instance.getBlock(pos).compare(Block.STRUCTURE_VOID)) {
            player.sendActionBar(Component.text("There is already someone hiding there", NamedTextColor.RED))
        } else {
            player.teleport(pos)

            player.sendActionBar(
                Component.text("You are hidden - Right click to get out")
            )

            instance.setBlock(pos, Block.STRUCTURE_VOID)
            player.getAttribute(Attribute.MOVEMENT_SPEED).baseValue = 0f
            player.setTag(DoorsGame.hidingTag, true)

            player.scheduler().buildTask { player.setTag(canLeaveTag, true) }.delay(Duration.ofMillis(600)).schedule()

            hidingTaskMap[player.uuid] = object : ExecutorRunnable(delay = Duration.ofMillis(5000), repeat = Duration.ofMillis(50), iterations = 200, executor = game.executor) {
                val titles = listOf("Get out", "get out", "Get out!", "Leave", "leave", "Leave!", "out", "Out", "Out!")

                val titleTimes = listOf(104, 128, 152, 160, 168, 178)

                val time = System.currentTimeMillis()

                override fun run() {
                    val currentIter = currentIteration.get()

                    if (currentIter == 0) {
                        player.playSound(Sound.sound(Key.key("entity.hide"), Sound.Source.MASTER, 1f, 1f), Sound.Emitter.self())
                    }

                    if (titleTimes.contains(currentIter)) {
                        player.showTitle(
                            Title.title(
                                Component.text(titles.random(), NamedTextColor.GRAY),
                                Component.empty(),
                                Title.Times.times(Duration.ZERO, Duration.ofMillis(100), Duration.ZERO)
                            )
                        )
                    }
                }

                override fun cancelled() {
                    hidingTaskMap[player.uuid]?.cancel()
                    hidingTaskMap.remove(player.uuid)

                    Achievement.AND_STAY_OUT.send(player)

                    player.damage(HideDamage(), 8f)

                    stopHiding(player, blockPosition, block, instance)
                }
            }
        }
    }
}