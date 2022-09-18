package dev.emortal.doors.block

import net.minestom.server.instance.block.Block
import net.minestom.server.instance.block.BlockHandler
import net.minestom.server.inventory.Inventory
import net.minestom.server.inventory.InventoryType
import net.minestom.server.utils.NamespaceID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class ChestHandler : BlockHandler {

    companion object {
        val chestNames = listOf("Chest", "Drawer", "Crate")
    }

    val main: ChestHandler

    constructor(doubleChest: Boolean) {
        main = this
        inventory = if(doubleChest) {
            Inventory(InventoryType.CHEST_4_ROW, chestNames.random())
        } else {
            Inventory(InventoryType.CHEST_2_ROW, chestNames.random())
        }
    }

    constructor(mainChest: ChestHandler) {
        main = mainChest
        inventory = Inventory(InventoryType.CHEST_2_ROW, chestNames.random())
    }

    override fun getNamespaceId(): NamespaceID = Block.CHEST.namespace()

    val inventory: Inventory
    val playersInside = AtomicInteger(0)
    val generatedLoot = AtomicBoolean(false)

}