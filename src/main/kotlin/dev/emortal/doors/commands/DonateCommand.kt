package dev.emortal.doors.commands

import dev.emortal.immortal.util.armify
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.command.builder.Command

object DonateCommand : Command("donate") {

    init {
        setDefaultExecutor { sender, context ->
            sender.sendMessage(
                Component.text()
                    .append(Component.text("Ko-Fi: ", TextColor.fromHexString("#FFF3DE")))
                    .append(
                        Component.text("ko-fi.com/emortal", NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                            .clickEvent(ClickEvent.openUrl("https://ko-fi.com/emortal"))
                    )

                    .append(Component.text("\nPatreon: ", TextColor.fromHexString("#FFF3DE")))
                    .append(
                        Component.text("patreon.com/emortaldev", NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                            .clickEvent(ClickEvent.openUrl("https://www.patreon.com/emortaldev"))
                    )
                    .armify()
            )
        }
    }

}