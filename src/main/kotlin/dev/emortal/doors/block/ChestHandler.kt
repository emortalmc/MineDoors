package dev.emortal.doors.block

import dev.emortal.doors.game.ChestLoot
import dev.emortal.doors.game.ChestLoot.addRandomly
import dev.emortal.doors.game.DoorsGame
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.minestom.server.coordinate.Point
import net.minestom.server.instance.block.Block
import net.minestom.server.instance.block.BlockHandler
import net.minestom.server.inventory.Inventory
import net.minestom.server.inventory.InventoryType
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.utils.NamespaceID
import java.util.concurrent.atomic.AtomicInteger

class ChestHandler(val game: DoorsGame, val position: Point) : BlockHandler {

    override fun getNamespaceId(): NamespaceID = Block.CHEST.namespace()

    val inventory: Inventory = Inventory(InventoryType.CHEST_2_ROW, "Chest")
        .also { inv ->
            ChestLoot.lootList.any { loot ->
                // only generate one item for a chest
                val item = loot()
                if (item != null) {
                    inv.addRandomly(item)
                    return@any true
                }
                false
            }

            inv.addInventoryCondition { player, slot, clickType, inventoryConditionResult ->
                val clickedItem = inv.getItemStack(slot)

                if (clickedItem.material() == Material.SPIDER_SPAWN_EGG) {
                    inventoryConditionResult.isCancel = true
                    return@addInventoryCondition
                }

                 if (clickedItem.material() != Material.AIR) {
                     player.playSound(Sound.sound(Key.key("currency.gold.increase"), Sound.Source.MASTER, 1f, 1f), Sound.Emitter.self())

                     inventoryConditionResult.isCancel = true
                     inv.setItemStack(slot, ItemStack.AIR)

                     val increase = when (clickedItem.material()) {
                         Material.SUNFLOWER -> 10
                         Material.GOLD_INGOT -> 50
                         Material.DIAMOND -> 200
                         else -> 0
                     }

                     game.refreshCoinCounts(player, increase, 0)
                }

            }
        }
    val playersInside = AtomicInteger(0)
//    val generatedLoot = AtomicBoolean(false)

}