package dev.emortal.doors.game

import dev.emortal.doors.block.ChestHandler
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.coordinate.Point
import net.minestom.server.entity.Player
import net.minestom.server.instance.block.Block
import net.minestom.server.inventory.Inventory
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.network.packet.server.play.BlockActionPacket
import net.minestom.server.sound.SoundEvent
import world.cepi.kstom.adventure.noItalic
import world.cepi.kstom.util.playSound
import java.util.concurrent.ThreadLocalRandom

object ChestLoot {

    fun Inventory.addRandomly(itemStack: ItemStack) {
        var randomSlot: Int

        var validSlot = false
        while (!validSlot) {
            randomSlot = ThreadLocalRandom.current().nextInt(size)

            if (getItemStack(randomSlot) == ItemStack.AIR) {
                validSlot = true
                setItemStack(randomSlot, itemStack)
            }
        }
    }

    val coinTypes = listOf(Material.DIAMOND, Material.GOLD_INGOT, Material.SUNFLOWER)

    val lootList = listOf(
        // Coins
        { probability(10.0) { ItemStack.of(Material.DIAMOND) } },
        { probability(20.0) { ItemStack.of(Material.GOLD_INGOT) } },
        { probability(40.0) { ItemStack.of(Material.SUNFLOWER).withDisplayName(Component.text("Coin").noItalic()) } },

        // Tools
        { probability(13.0) { ItemStack.of(Material.FLINT_AND_STEEL) } },

        // Timothy
        { probability(0.5) { ItemStack.of(Material.SPIDER_SPAWN_EGG).withDisplayName(Component.text("Timothy", NamedTextColor.RED).noItalic()) } }
    )

    fun openChest(game: DoorsGame, player: Player, block: Block, blockPosition: Point) {
        val handler = block.handler() as? ChestHandler ?: return

        if (handler.playersInside.get() > 0) {
            player.sendActionBar(Component.text("There is already someone looting this chest"))
            player.playSound(Sound.sound(SoundEvent.BLOCK_CHEST_LOCKED, Sound.Source.MASTER, 1f, 1.2f), blockPosition.add(0.5))
            return
        }

        player.openInventory(handler.inventory)

        val playersInside = handler.playersInside.incrementAndGet().toByte()
        val packet = BlockActionPacket(blockPosition, 1, playersInside, block)
        player.instance?.sendGroupedPacket(packet)

        game.playerChestMap[player.uuid] = blockPosition

        player.instance?.playSound(
            Sound.sound(SoundEvent.BLOCK_CHEST_OPEN, Sound.Source.BLOCK, 1f, 1f),
            blockPosition.add(0.5)
        )
    }

    fun freeChest(player: Player, block: Block, blockPosition: Point) {
        val handler = block.handler() as? ChestHandler ?: return
        val playersInside = handler.playersInside.decrementAndGet().toByte()
        val packet = BlockActionPacket(blockPosition, 1, playersInside, block)
        player.instance?.sendGroupedPacket(packet)
    }

    private fun probability(percent: Double, block: () -> ItemStack): ItemStack? {
        val randomNum = ThreadLocalRandom.current().nextDouble(100.0)

        return if (randomNum <= percent) {
            block()
        } else null
    }
}