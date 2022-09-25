package dev.emortal.doors.commands

import dev.emortal.doors.Main.Companion.doorsConfig
import dev.emortal.immortal.util.armify
import dev.emortal.immortal.util.centerText
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.command.builder.Command

object CreditsCommand : Command("credits") {

    init {
        setDefaultExecutor { sender, context ->
            sender.sendMessage(
                Component.text()
                    .append(Component.text(centerText("Credits", true), NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD))

                    .append(Component.text("\n\n  Building", TextColor.fromHexString("#FFF3DE")))
                    .append(Component.text("\n - emortal", NamedTextColor.GRAY))
                    .append(Component.text("\n - DasLixou", NamedTextColor.GRAY))

                    .append(Component.text("\n\n  Development", TextColor.fromHexString("#FFF3DE")))
                    .append(Component.text("\n - emortal", NamedTextColor.GRAY))
                    .append(Component.text("\n - DasLixou", NamedTextColor.GRAY))

                    .append(Component.text("\n - the pog fish (resource pack help)", NamedTextColor.GRAY))

                    .append(Component.text("\n\n  Donators ❤", NamedTextColor.LIGHT_PURPLE))
                    .also {
                        doorsConfig.donators.forEach { donator ->
                            it.append(
                                Component.text("\n - $donator", NamedTextColor.GRAY)
                            )
                        }
                    }

                    .append(Component.text(" " + centerText("\n\nOriginal game created by LSplash Games"), TextColor.color(90, 90, 90)))
                    .armify()
            )
        }
    }

}