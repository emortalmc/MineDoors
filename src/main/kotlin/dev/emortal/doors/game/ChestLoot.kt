package dev.emortal.doors.game

import dev.emortal.doors.block.ChestHandler
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.minestom.server.coordinate.Point
import net.minestom.server.entity.Player
import net.minestom.server.instance.block.Block
import net.minestom.server.sound.SoundEvent

object ChestLoot {
    private val openChests: HashMap<Player, ChestHandler> = HashMap()

    fun openChest(player: Player, block: Block, blockPosition: Point) {
        val handler = (block.handler() as? ChestHandler ?: return).main

        if (handler.playersInside.get() > 0) {
            player.sendActionBar(Component.text("There is already someone looting this chest"))
            return
        }

        openChests[player] = handler
        player.openInventory(handler.inventory)

        handler.playersInside.incrementAndGet()

        player.instance?.playSound(
            Sound.sound(SoundEvent.BLOCK_CHEST_OPEN, Sound.Source.BLOCK, 1f, 1f),
            blockPosition.add(0.5)
        )
    }

    fun freeChest(player: Player) {
        openChests[player]?.playersInside?.decrementAndGet()
    }
}