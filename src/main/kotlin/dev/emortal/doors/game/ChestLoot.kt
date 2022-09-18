package dev.emortal.doors.game

import dev.emortal.doors.block.ChestHandler
import dev.emortal.doors.util.placeItem
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.minestom.server.coordinate.Point
import net.minestom.server.entity.Player
import net.minestom.server.instance.block.Block
import net.minestom.server.inventory.Inventory
import net.minestom.server.inventory.InventoryType
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.sound.SoundEvent
import world.cepi.kstom.util.setItemStack

object ChestLoot {
    private val lootList = listOf<(Inventory) -> Boolean>(
        // Coins
        { inv -> probability(60) { inv.placeItem(ItemStack.of(Material.SUNFLOWER).withAmount((1..7*2).random().floorDiv(3))) } },
        { inv -> probability(27) { inv.placeItem(ItemStack.of(Material.GOLD_INGOT).withAmount((1..4*2).random().floorDiv(3))) } },
        { inv -> probability(9) { inv.placeItem(ItemStack.of(Material.DIAMOND)) } },
        // Tools
        { inv -> probability(19) { inv.placeItem(ItemStack.of(Material.FLINT_AND_STEEL).withMeta { it.damage((1..60).random()) }) } },
        // Timothy
        { inv -> probability(2) { inv.placeItem(ItemStack.of(Material.SPIDER_SPAWN_EGG)) } }
    )

    private fun generateLoot(inventory: Inventory) {
        lootList.shuffled().forEach { loot ->
            if(loot(inventory)) {
                return
            }
        }
    }

    private val openChests: HashMap<Player, ChestHandler> = HashMap()

    fun openChest(player: Player, block: Block, blockPosition: Point) {
        val handler = (block.handler() as? ChestHandler ?: return).main

        if (handler.playersInside.get() > 0) {
            player.sendActionBar(Component.text("There is already someone looting this chest"))
            return
        }

        openChests[player] = handler

        if (!handler.generatedLoot.get()) {
            generateLoot(handler.inventory)
            if (handler.inventory.inventoryType == InventoryType.CHEST_4_ROW) {
                generateLoot(handler.inventory)
            }
            handler.generatedLoot.set(true)
        }

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

    private fun probability(percent: Int, block: () -> Unit = {}): Boolean {
        val randomNum = (1..100).random()

        return if (randomNum <= percent) {
            block()
            true
        } else false
    }
}