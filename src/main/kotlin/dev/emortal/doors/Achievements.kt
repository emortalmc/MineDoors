package dev.emortal.doors

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.advancements.Advancement
import net.minestom.server.advancements.AdvancementRoot
import net.minestom.server.advancements.FrameType
import net.minestom.server.entity.Player
import net.minestom.server.item.Material
import world.cepi.kstom.Manager

object Achievements {

    private fun description(first: String, second: String): Component = Component.text()
        .append(Component.text(first, NamedTextColor.GRAY))
        .append(Component.newline())
        .append(Component.text(second, NamedTextColor.GOLD))
        .build()

    fun create(player: Player) {


        val root = AdvancementRoot(
            Component.text("Welcome"),
            description("Enjoy your stay!", "Join for the first time."),
            Material.DARK_OAK_DOOR,
            FrameType.TASK,
            0f, 0f,
            "minecraft:textures/block/dark_oak_planks.png"
        )
        root.isAchieved = true
        val tab = Manager.advancement.createTab("doors", root)


        val buddy = Advancement(
            Component.text("Buddy System"),
            description("Everything's better with friends!", "Play a run with a friend."),
            Material.RED_TULIP,
            FrameType.TASK,
            1f, 0f
        )

        // Entities
        val rush = Advancement(
            Component.text("Out Of My Way!"),
            description("I'm walkin' here!", "Successfully survive Rush."),
            Material.POTION,
            FrameType.TASK,
            1f,
            1f
        )
        val screech = Advancement(
            Component.text("I See You"),
            description("Peek-a-boo!", "Dodge Screech's attack."),
            Material.BAT_SPAWN_EGG,
            FrameType.TASK,
            2f,
            1f
        )
        val eyes = Advancement(
            Component.text("Look At Me"),
            description("Last chance to look at me.", "Survive the Eyes."),
            Material.ENDER_EYE,
            FrameType.TASK,
            2.5f,
            2f
        )
        val halt = Advancement(
            Component.text("Two Steps Forward..."),
            description("...and one step back.", "Survive Halt."),
            Material.BARRIER,
            FrameType.TASK,
            3.5f,
            2f
        )
        val hide = Advancement(
            Component.text("Eviction Notice"),
            description("And stay out!", "Get pushed out of a hiding spot by Hide."),
            Material.SPRUCE_DOOR,
            FrameType.TASK,
            4f,
            1f
        )


        // Deaths
        val deathOne = Advancement(
            Component.text("One Of Many"),
            description("You're just getting started.", "Encounter your first death."),
            Material.SKELETON_SKULL,
            FrameType.TASK,
            1f,
            -1f
        )
        val deathTen = Advancement(
            Component.text("Ten Of Many"),
            description("You'll get used to it.", "Encounter your tenth death."),
            Material.SKELETON_SKULL,
            FrameType.TASK,
            2f,
            -1f
        )
        val deathHundred = Advancement(
            Component.text("Hundred Of Many"),
            description("That's more like it!", "Encounter your hundredth death."),
            Material.SKELETON_SKULL,
            FrameType.CHALLENGE,
            3f,
            -1f
        )

        tab.createAdvancement("buddysystem", buddy, root)

        tab.createAdvancement("deathone", deathOne, root)
        tab.createAdvancement("deathten", deathTen, deathOne)
        tab.createAdvancement("deathhundred", deathHundred, deathTen)

        tab.createAdvancement("rush", rush, root)
        tab.createAdvancement("screech", screech, rush)
        tab.createAdvancement("eyes", eyes, screech)
        tab.createAdvancement("halt", halt, eyes)
        tab.createAdvancement("hide", hide, halt)

        tab.addViewer(player)



    }

}