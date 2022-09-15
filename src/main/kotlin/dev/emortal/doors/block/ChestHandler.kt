package dev.emortal.doors.block

import net.minestom.server.instance.block.Block
import net.minestom.server.instance.block.BlockHandler
import net.minestom.server.inventory.Inventory
import net.minestom.server.inventory.InventoryType
import net.minestom.server.utils.NamespaceID
import java.util.concurrent.atomic.AtomicInteger

class ChestHandler : BlockHandler {

    val main: ChestHandler

    constructor() {
        main = this
    }

    constructor(mainChest: ChestHandler) {
        main = mainChest
    }

    override fun getNamespaceId(): NamespaceID = Block.CHEST.namespace()

    val inventory: Inventory = Inventory(InventoryType.CHEST_2_ROW, "Chest")
    val playersInside = AtomicInteger(0)

    companion object {
        fun create(): Block = Block.CHEST.withHandler(ChestHandler())
    }

}