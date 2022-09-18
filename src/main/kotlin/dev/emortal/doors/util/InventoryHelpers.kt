package dev.emortal.doors.util

import net.minestom.server.inventory.Inventory
import net.minestom.server.item.ItemStack

fun Inventory.placeItem(item: ItemStack) = setItemStack((0 until size).random(), item)
