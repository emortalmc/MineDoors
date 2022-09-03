package dev.emortal.doors.game

import dev.emortal.doors.damage.HideDamage
import dev.emortal.doors.pathfinding.offset
import dev.emortal.doors.pathfinding.rotate
import dev.emortal.doors.pathfinding.yaw
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.title.Title
import net.minestom.server.attribute.Attribute
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.sound.SoundEvent
import net.minestom.server.tag.Tag
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import net.minestom.server.utils.Direction
import java.time.Duration
import java.util.*
import java.util.concurrent.ConcurrentHashMap

data class Closet(val doorDirection: Direction, val rotatedDir: Direction, val yOffset: Double, val pos: Pos) {
    companion object {
        val hidingTaskMap = ConcurrentHashMap<UUID, Task>()
        val canLeaveTag = Tag.Boolean("canLeaveCloset")

        fun getFromDoor(block: Block, blockPosition: Point): Closet {
            val doorDirection = Direction.valueOf(block.getProperty("facing").uppercase())
            val rotatedDir = doorDirection.rotate()
            val yOffset = if (block.getProperty("half") == "upper") -1.0 else 0.0

            return Closet(doorDirection, rotatedDir, yOffset, Pos(blockPosition.add(0.5, yOffset, 0.5)
                .sub(doorDirection.offset())
                .let {
                    if (block.getProperty("hinge") == "left") it.add(rotatedDir.normalX().toDouble() * 0.5, 0.0, rotatedDir.normalZ().toDouble() * 0.5)
                    else it.sub(rotatedDir.normalX().toDouble() * 0.5, 0.0, rotatedDir.normalZ().toDouble() * 0.5)
                },
                doorDirection.yaw(), 0f))
        }
        fun getFromTrapdoor(block: Block, blockPosition: Point, instance: Instance): Closet? {
            val trapDirection = Direction.valueOf(block.getProperty("facing").uppercase())
            val open = block.getProperty("open").uppercase();
            val innerCloset = blockPosition.sub(trapDirection.offset())
            Direction.values().forEach { facing ->
                val pos = innerCloset.add(facing.offset())
                val b = instance.getBlock(pos)
                println("" + pos + " | " +  b.registry().namespace().toString())
                if(b.compare(Block.SPRUCE_DOOR)) {
                    return getFromDoor(b, pos)
                }
            }
            return null
        }
    }

    fun stopHiding(player: Player, blockPosition: Point, block: Block, instance: Instance) {
        player.sendActionBar(Component.empty())

        player.removeTag(DoorsGame.hidingTag)
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

            player.scheduler().buildTask { player.setTag(canLeaveTag, true) }.delay(Duration.ofMillis(350)).schedule()

            hidingTaskMap[player.uuid] = player.scheduler().buildTask(object : Runnable {
                val titles = listOf("Get out", "get out", "Get out!", "Leave", "leave", "Leave!", "out", "Out", "Out!")
                var iter = 0
                var mod = 25

                override fun run() {
                    if (mod <= 0 || iter % mod == 0) {
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

                        Achievement.AND_STAY_OUT.send(player)

                        player.damage(HideDamage(), 8f)

                        stopHiding(player, blockPosition, block, instance)
                    }

                    iter++
                }
            }).delay(Duration.ofSeconds(5)).repeat(TaskSchedule.tick(1)).schedule()
        }
    }
}