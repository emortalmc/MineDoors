package dev.emortal.doors

import dev.emortal.doors.game.Achievement
import net.kyori.adventure.text.Component
import net.minestom.server.advancements.AdvancementRoot
import net.minestom.server.advancements.AdvancementTab
import net.minestom.server.advancements.FrameType
import net.minestom.server.entity.Player
import net.minestom.server.item.Material
import world.cepi.kstom.Manager
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object Achievements {

    private val tabMap = ConcurrentHashMap<UUID, AdvancementTab>()

    fun create(player: Player) {


        val root = AdvancementRoot(
            Component.text("Welcome"),
            Achievement.styleDescription("Enjoy your stay!", "Join for the first time."),
            Material.DARK_OAK_DOOR,
            FrameType.TASK,
            0f, 0f,
            "minecraft:textures/block/dark_oak_planks.png"
        )
        root.isAchieved = true
        val tab = Manager.advancement.createTab("doors", root)


        val buddy = Achievement.BUDDY.asAdvancement()

        // Entities
        val rush = Achievement.OUT_OF_MY_WAY.asAdvancement()
        val screech = Achievement.I_SEE_YOU.asAdvancement()
        val eyes = Achievement.LOOK_AT_ME.asAdvancement()
        val halt = Achievement.TWO_STEPS_FORWARD.asAdvancement()
        val hide = Achievement.AND_STAY_OUT.asAdvancement()


        // Deaths
        val deathOne = Achievement.DEATH_ONE.asAdvancement()
        val deathTen = Achievement.DEATH_TEN.asAdvancement()
        val deathHundred = Achievement.DEATH_HUNDRED.asAdvancement()

        tab.createAdvancement("buddy_system", buddy, root)

        tab.createAdvancement("death_one", deathOne, root)
        tab.createAdvancement("death_ten", deathTen, deathOne)
        tab.createAdvancement("death_hundred", deathHundred, deathTen)

        tab.createAdvancement("rush", rush, root)
        tab.createAdvancement("screech", screech, rush)
        tab.createAdvancement("eyes", eyes, screech)
        tab.createAdvancement("halt", halt, eyes)
        tab.createAdvancement("hide", hide, halt)

        tab.addViewer(player)

        tabMap[player.uuid] = tab

    }

    fun remove(player: Player) {
        tabMap[player.uuid]?.removeViewer(player)
        tabMap.remove(player.uuid)
    }

}