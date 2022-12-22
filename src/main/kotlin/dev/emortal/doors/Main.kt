package dev.emortal.doors

import com.sun.tools.javac.Main
import dev.emortal.doors.Main.Companion.configPath
import dev.emortal.doors.Main.Companion.doorsConfig
import dev.emortal.doors.block.SignHandler
import dev.emortal.doors.commands.CreditsCommand
import dev.emortal.doors.commands.DonateCommand
import dev.emortal.doors.config.DoorsConfig
import dev.emortal.doors.game.DoorsGame
import dev.emortal.doors.lobby.DoorsLobby
import dev.emortal.doors.schematic.RoomSchematic
import dev.emortal.immortal.ImmortalExtension
import dev.emortal.immortal.config.ConfigHelper
import dev.emortal.immortal.game.GameManager
import dev.emortal.immortal.game.GameManager.game
import dev.emortal.immortal.util.MinestomRunnable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.hollowcube.util.schem.SchematicReader
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.event.player.PlayerResourcePackStatusEvent
import net.minestom.server.extras.MojangAuth
import net.minestom.server.extras.bungee.BungeeCordProxy
import net.minestom.server.extras.velocity.VelocityProxy
import net.minestom.server.instance.DynamicChunk
import net.minestom.server.instance.Instance
import net.minestom.server.resourcepack.ResourcePackStatus
import net.minestom.server.utils.NamespaceID
import net.minestom.server.world.DimensionType
import org.tinylog.kotlin.Logger
import world.cepi.kstom.Manager
import world.cepi.kstom.command.register
import world.cepi.kstom.event.listenOnly
import world.cepi.kstom.util.asPos
import world.cepi.kstom.util.chunksInRange
import world.cepi.kstom.util.register
import java.io.IOException
import java.io.InputStream
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.time.Duration
import java.util.*
import java.util.stream.Collectors
import kotlin.io.path.absolutePathString
import kotlin.io.path.isDirectory
import kotlin.io.path.nameWithoutExtension


fun Instance.relight(position: Pos) {
    val chunks = chunksInRange(position.asPos(), 2).mapNotNull { getChunk(it.first, it.second) as? DynamicChunk }

    chunks.forEach {
        it.invalidate()
        it.sections.forEach {
            it.blockLight.invalidate()
        }
    }
    chunks.forEach {
        it.sendChunk()
        sendGroupedPacket(it.createLightPacket())
    }
}

val numSchems = Files.list(Path.of("./numbers/"))
    .filter { !it.isDirectory() }
    .collect(Collectors.toSet()).map {
        Logger.info("Reading number ${it}")

        SchematicReader.read(it)
    }

val schematics = Files.list(Path.of("./rooms/"))
    .filter { !it.isDirectory() }
    .collect(Collectors.toSet()).map {
        Logger.info("Reading schematic ${it}")

        RoomSchematic(SchematicReader.read(it), it.nameWithoutExtension)
    }

val seekSchematics = Files.list(Path.of("./rooms/seek/"))
    .filter { !it.isDirectory() }
    .collect(Collectors.toSet()).map {
        Logger.info("Reading seek schematic ${it}")

        RoomSchematic(SchematicReader.read(it), it.nameWithoutExtension)
    }

// placeholder - big schematic for door bound comparison
val bigSchem = RoomSchematic(SchematicReader.read(Path.of("./rooms/roomcross.schem")), "roomcross")

val doorSchem = SchematicReader.read(Path.of("./doordesign.schem"))

val startingSchem = RoomSchematic(SchematicReader.read(Path.of("./startingroom.schem")), "roomstarting")
val endingSchem = RoomSchematic(SchematicReader.read(Path.of("./roomending.schem")), "roomending")

class Main() {
    companion object {
        val configPath = Path.of("doorsconfig.json")
        var doorsConfig = ConfigHelper.initConfigFile(configPath, DoorsConfig(listOf()))
    }
}

fun main() {

    Logger.info("Starting DOORS :)")
    Logger.info(configPath.absolutePathString())

    // Automatically refresh config
    val watchDirectory = Path.of("./")
    val watchService = FileSystems.getDefault().newWatchService()
    watchDirectory.register(watchService, StandardWatchEventKinds.ENTRY_CREATE,
        StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE)

    CoroutineScope(Dispatchers.IO).launch {
        while (true) {
            val watchKey = watchService.take()

            for (event in watchKey.pollEvents()) {
                if ((event.context() as Path) == configPath) {
                    Logger.info("Refreshed config")
                    doorsConfig = ConfigHelper.initConfigFile(configPath, DoorsConfig(listOf()))
                }

            }

            if (!watchKey.reset()) {
                watchKey.cancel()
                watchService.close()
                break
            }
        }
    }

    val minecraftServer = MinecraftServer.init()
    val global = Manager.globalEvent

    System.setProperty("debug", "true")
    System.setProperty("debuggame", "doorslobby")

    ImmortalExtension.init(global)

    val dim = DimensionType.builder(NamespaceID.from("nolight")).skylightEnabled(false).build()
    val dim2 = DimensionType.builder(NamespaceID.from("fullbrighttt")).ambientLight(2f).build()
    Manager.dimensionType.addDimension(dim)
    Manager.dimensionType.addDimension(dim2)

    MinecraftServer.setBrandName("§dMinestom ${MinecraftServer.VERSION_NAME}")

    GameManager.registerGame<DoorsLobby>(
        "doorslobby",
        Component.text("Doors Lobby"),
        showsInSlashPlay = false
    )

    GameManager.registerGame<DoorsGame>(
        "doors",
        Component.text("Doors"),
        showsInSlashPlay = true, //TODO: change
    )

    SignHandler.register()

    MinecraftServer.getGlobalEventHandler().listenOnly<PlayerResourcePackStatusEvent> {
        when (status) {
            ResourcePackStatus.SUCCESS -> {
                player.sendActionBar(Component.text("Resource pack applied successfully"))

                val lobbyGame = player.game as DoorsLobby

                lobbyGame.musicTasks[player.uuid] = object : MinestomRunnable(delay = Duration.ofSeconds(3), repeat = Duration.ofMillis(213_300), group = lobbyGame.runnableGroup) {
                    override fun run() {
                        player.playSound(Sound.sound(Key.key("music.dawnofthedoors"), Sound.Source.MASTER, 0.4f, 1f), Sound.Emitter.self())
                    }
                }
            }

            ResourcePackStatus.DECLINED -> {
                player.kick(Component.text("The resource pack is required. You can ignore the prompt by allowing server resource packs."))
            }

            ResourcePackStatus.FAILED_DOWNLOAD -> {
                player.kick(Component.text("The resource pack failed to download. Please contact a staff member.\ndiscord.gg/TZyuMSha96"))
            }

            else -> {}
        }
    }

    val properties = loadProperties()

    System.setProperty("minestom.chunk-view-distance", "8")

    val onlineMode = properties.getProperty("online-mode").toBoolean()
    val address: String = properties.getProperty("address")
    val port: Int = properties.getProperty("port").toInt()
    val compressionThreshold: Int = properties.getProperty("compression-threshold").toInt()

    val proxy: String = properties.getProperty("proxy").lowercase()
    val proxySecret: String = properties.getProperty("proxy-secret")

    when (proxy) {
        "velocity" -> {
            VelocityProxy.enable(proxySecret)
            Logger.info("Enabling velocity forwarding")
        }

        "bungee" -> {
            BungeeCordProxy.enable()
            Logger.info("Enabling bungee forwarding")
        }
    }


    if (onlineMode) {
        Logger.info("Starting server with online mode enabled!")
        MojangAuth.init()
    }

    MinecraftServer.setCompressionThreshold(compressionThreshold)

    StopCommand.register()
    CreditsCommand.register()
    DonateCommand.register()

    minecraftServer.start(address, port)
}

private val propertiesPath = Path.of("./server.properties")
fun loadProperties(): Properties {
    val properties = Properties()
    try {
        if (Files.exists(propertiesPath)) {
            properties.load(Files.newInputStream(propertiesPath))
        } else {
            val inputStream: InputStream =
                Main::class.java.classLoader.getResourceAsStream("server.properties")
            properties.load(inputStream)
            properties.store(Files.newOutputStream(propertiesPath), "Minestom " + MinecraftServer.VERSION_NAME)
        }
    } catch (e: IOException) {
        MinecraftServer.getExceptionManager().handleException(e)
    }
    return properties
}