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
import dev.emortal.doors.pathfinding.rotate
import dev.emortal.doors.schematic.RoomSchematic
import dev.emortal.immortal.ImmortalExtension
import dev.emortal.immortal.config.ConfigHelper
import dev.emortal.immortal.config.GameOptions
import dev.emortal.immortal.game.GameManager
import dev.emortal.immortal.game.WhenToRegisterEvents
import dev.hypera.scaffolding.schematic.impl.SpongeSchematic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.extras.MojangAuth
import net.minestom.server.extras.bungee.BungeeCordProxy
import net.minestom.server.extras.velocity.VelocityProxy
import net.minestom.server.instance.DynamicChunk
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.utils.Direction
import net.minestom.server.utils.NamespaceID
import net.minestom.server.world.DimensionType
import org.tinylog.kotlin.Logger
import world.cepi.kstom.Manager
import world.cepi.kstom.command.register
import world.cepi.kstom.util.asPos
import world.cepi.kstom.util.chunksInRange
import world.cepi.kstom.util.register
import java.io.IOException
import java.io.InputStream
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.util.*
import java.util.stream.Collectors
import kotlin.io.path.absolutePathString
import kotlin.io.path.inputStream
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
        val schem = SpongeSchematic()
        schem.read(it.inputStream())

        Logger.info("Reading number ${it}")

        schem
    }

val schematics = Files.list(Path.of("./rooms/"))
    .filter { !it.isDirectory() }
    .collect(Collectors.toSet()).map {
        val schem = SpongeSchematic()
        schem.read(it.inputStream())

        Logger.info("Reading schematic ${it}")

        RoomSchematic(schem, it.nameWithoutExtension)
    }

val seekSchematics = Files.list(Path.of("./rooms/seek/"))
    .filter { !it.isDirectory() }
    .collect(Collectors.toSet()).map {
        val schem = SpongeSchematic()
        schem.read(it.inputStream())

        Logger.info("Reading seek schematic ${it}")

        RoomSchematic(schem, it.nameWithoutExtension)
    }

val doorSchem = SpongeSchematic().also {
    it.read(Path.of("./doordesign.schem").inputStream())
}

val startingSchem = RoomSchematic(SpongeSchematic().also {
    it.read(Path.of("./startingroom.schem").inputStream())
}, "roomstarting")
val endingSchem = RoomSchematic(SpongeSchematic().also {
    it.read(Path.of("./roomending.schem").inputStream())
}, "roomending")

/**
 * @return New position and block
 */
fun applyRotationToBlock(offset: Point, x: Int, y: Int, z: Int, block: Block, direction: Direction, schematic: SpongeSchematic): Pair<Point, Block> {
    lateinit var newBlockPos: Point
    var modifiedBlock = block

    when (direction) {
        // No rotation
        Direction.NORTH -> {
            newBlockPos = Vec(x.toDouble(), y.toDouble(), z.toDouble()).add(offset)
        }
        // Rotate 90
        Direction.WEST -> {
            val facingProperty = block.getProperty("facing")
            if (facingProperty != null) {
                val dir = Direction.valueOf(facingProperty.uppercase()).rotate().opposite()
                modifiedBlock = modifiedBlock.withProperty("facing", dir.name.lowercase())
            }

            // Fence rotation
            if (block.getProperty("north") != null) {
                val northProperty = block.getProperty("north")
                val eastProperty = block.getProperty("east")
                val southProperty = block.getProperty("south")
                val westProperty = block.getProperty("west")

                modifiedBlock = modifiedBlock.withProperties(mapOf(
                    "north" to eastProperty,
                    "east" to southProperty,
                    "south" to westProperty,
                    "west" to northProperty
                ))
            }

            newBlockPos = Vec(z.toDouble(), y.toDouble(), ((schematic.width - x) - schematic.width).toDouble()).add(offset)
        }
        // Rotate 180
        Direction.SOUTH -> {
            val facingProperty = block.getProperty("facing")
            if (facingProperty != null) {
                val dir = Direction.valueOf(facingProperty.uppercase()).opposite()
                modifiedBlock = modifiedBlock.withProperty("facing", dir.name.lowercase())
            }

            // Fence rotation
            if (block.getProperty("north") != null) {
                val northProperty = block.getProperty("north")
                val eastProperty = block.getProperty("east")
                val southProperty = block.getProperty("south")
                val westProperty = block.getProperty("west")

                modifiedBlock = modifiedBlock.withProperties(mapOf(
                    "north" to southProperty,
                    "east" to westProperty,
                    "south" to northProperty,
                    "west" to eastProperty
                ))
            }

            newBlockPos = Vec(((schematic.width - x) - schematic.width).toDouble(), y.toDouble(), ((schematic.length - z) - schematic.length).toDouble()).add(offset)
        }
        // Rotate -90
        Direction.EAST -> {
            val facingProperty =  block.getProperty("facing")
            if (facingProperty != null) {
                val dir = Direction.valueOf(facingProperty.uppercase()).rotate()
                modifiedBlock = modifiedBlock.withProperty("facing", dir.name.lowercase())
            }

            // Fence rotation
            if (block.getProperty("north") != null) {
                val northProperty = block.getProperty("north")
                val eastProperty = block.getProperty("east")
                val southProperty = block.getProperty("south")
                val westProperty = block.getProperty("west")

                modifiedBlock = modifiedBlock.withProperties(mapOf(
                    "north" to westProperty,
                    "east" to northProperty,
                    "south" to eastProperty,
                    "west" to southProperty
                ))
            }

            newBlockPos = Vec(((schematic.length - z) - schematic.length).toDouble(), y.toDouble(), x.toDouble()).add(offset)
        }
        else -> {}
    }

    return newBlockPos to modifiedBlock
}

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
        showsInSlashPlay = false,
        canSpectate = false,
        whenToRegisterEvents = WhenToRegisterEvents.IMMEDIATELY,
        GameOptions(
            minPlayers = 0,
            maxPlayers = 30,
            showScoreboard = false
        )
    )

    GameManager.registerGame<DoorsGame>(
        "doors",
        Component.text("Doors"),
        showsInSlashPlay = true, //TODO: change
        canSpectate = true,
        whenToRegisterEvents = WhenToRegisterEvents.IMMEDIATELY,
        GameOptions(
            minPlayers = 1,
            maxPlayers = 4,
            showScoreboard = false
        )
    )

    SignHandler.register()

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