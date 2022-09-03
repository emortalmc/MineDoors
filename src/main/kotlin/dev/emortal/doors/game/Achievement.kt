package dev.emortal.doors.game

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.advancements.Advancement
import net.minestom.server.advancements.FrameType
import net.minestom.server.advancements.notifications.Notification
import net.minestom.server.advancements.notifications.NotificationCenter
import net.minestom.server.entity.Player
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material

data class Achievement(val title: String, val material: Material, val firstDesc: String, val secondDesc: String, val x: Float, val y: Float) {
    companion object {
        val BUDDY = Achievement("Buddy System", Material.RED_TULIP, "Everything's better with friends!", "Play a run with a friend.", 1f, 0f)

        val OUT_OF_MY_WAY = Achievement("Out Of My Way!", Material.POTION, "I'm walkin' here!", "Successfully survive Rush.", 1f, 1f)
        val I_SEE_YOU = Achievement("I See You", Material.BAT_SPAWN_EGG, "Peek-a-boo!", "Dodge Screech's attack.", 2f, 1f)
        val LOOK_AT_ME = Achievement("Look At Me", Material.ENDER_EYE, "Last chance to look at me.", "Survive the Eyes.", 2.5f, 2f)
        val TWO_STEPS_FORWARD = Achievement("Two Steps Forward...", Material.BARRIER, "...and one step back.", "Survive Halt.", 3.5f, 2f)
        val AND_STAY_OUT = Achievement("Eviction Notice", Material.SPRUCE_DOOR, "And stay out!", "Get pushed out of a hiding spot by Hide.", 4f, 1f)

        val DEATH_ONE = Achievement("One Of Many", Material.SKELETON_SKULL, "You're just getting started.", "Encounter your first death.", 1f, -1f)
        val DEATH_TEN = Achievement("Ten Of Many", Material.SKELETON_SKULL, "You'll get used to it.", "Encounter your tenth death.", 2f, -1f)
        val DEATH_HUNDRED= Achievement("Hundred Of Many", Material.SKELETON_SKULL, "That's more like it!", "Encounter your hundredth death.", 3f, -1f)

        fun styleDescription(first: String, second: String): Component = Component.text()
            .append(Component.text(first, NamedTextColor.GRAY))
            .append(Component.newline())
            .append(Component.text(second, NamedTextColor.GOLD))
            .build()
    }

    fun send(player: Player) {
        NotificationCenter.send(
            Notification(
                Component.text(title, NamedTextColor.WHITE),
                FrameType.TASK,
                ItemStack.of(material)),
            player)
    }

    fun asAdvancement(): Advancement {
        return Advancement(
            Component.text(title),
            styleDescription(firstDesc, secondDesc),
            material,
            FrameType.TASK,
            x, y
        )
    }
}