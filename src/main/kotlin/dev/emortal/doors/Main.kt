package dev.emortal.doors

import com.sun.tools.javac.Main
import dev.emortal.doors.block.SignHandler
import dev.emortal.doors.game.DoorsGame
import dev.emortal.doors.lobby.DoorsLobby
import dev.emortal.doors.pathfinding.rotate
import dev.emortal.immortal.ImmortalExtension
import dev.emortal.immortal.config.GameOptions
import dev.emortal.immortal.game.GameManager
import dev.emortal.immortal.game.WhenToRegisterEvents
import dev.hypera.scaffolding.schematic.impl.SpongeSchematic
import net.kyori.adventure.text.Component
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.GameMode
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.extras.MojangAuth
import net.minestom.server.extras.bungee.BungeeCordProxy
import net.minestom.server.extras.velocity.VelocityProxy
import net.minestom.server.instance.DynamicChunk
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.potion.Potion
import net.minestom.server.potion.PotionEffect
import net.minestom.server.utils.Direction
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
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.stream.Collectors
import kotlin.io.path.inputStream
import kotlin.io.path.isDirectory


fun Instance.relight(position: Pos) {
    val chunks = chunksInRange(position.asPos(), 3).mapNotNull { getChunk(it.first, it.second) as? DynamicChunk }

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

val schematics = Files.list(Path.of("./rooms/"))
    .filter { !it.isDirectory() }
    .collect(Collectors.toSet()).map {
        val schem = SpongeSchematic()
        schem.read(it.inputStream())

        Logger.info("Reading schematic ${it}")

        schem
    }

val doorSchem = SpongeSchematic().also {
    it.read(Path.of("./doordesign.schem").inputStream())
}

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



fun main() {

    val minecraftServer = MinecraftServer.init()
    val instanceManager = Manager.instance
    val global = Manager.globalEvent

    System.setProperty("debug", "true")
    System.setProperty("debuggame", "doorslobby")

    try {

        ImmortalExtension.init(global)
    } catch (e: NoSuchFieldError) {
        Logger.info("problem ignored! :)")
    }

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

    val dim = DimensionType.builder(NamespaceID.from("nolight")).skylightEnabled(false).build()
    Manager.dimensionType.addDimension(dim)

    val schematic = SpongeSchematic()
    schematic.read(Path.of("./doors.schem").inputStream())

    global.listenOnly<PlayerSpawnEvent> {

        player.addEffect(Potion(PotionEffect.JUMP_BOOST, 200.toByte(), Short.MAX_VALUE.toInt()))

        player.gameMode = GameMode.ADVENTURE

    }

    SignHandler.register()

    val properties = loadProperties()

    MinecraftServer.setChunkViewDistance(15)

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

    minecraftServer.start(address, port)
}

private val configPath = Path.of("./server.properties")
fun loadProperties(): Properties {
    val properties = Properties()
    try {
        if (Files.exists(configPath)) {
            properties.load(Files.newInputStream(configPath))
        } else {
            val inputStream: InputStream =
                Main::class.java.classLoader.getResourceAsStream("server.properties")
            properties.load(inputStream)
            properties.store(Files.newOutputStream(configPath), "Minestom " + MinecraftServer.VERSION_NAME)
        }
    } catch (e: IOException) {
        MinecraftServer.getExceptionManager().handleException(e)
    }
    return properties
}