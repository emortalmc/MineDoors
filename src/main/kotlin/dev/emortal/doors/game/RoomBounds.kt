package dev.emortal.doors.game

import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Player
import world.cepi.particle.Particle
import world.cepi.particle.ParticleType
import world.cepi.particle.data.OffsetAndSpeed
import world.cepi.particle.renderer.Renderer
import world.cepi.particle.showParticle
import java.awt.Rectangle
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class RoomBounds(val topLeft: Point, val bottomRight: Point) {

    fun print(player: Player) {
        val firstMinX = min(topLeft.blockX(), bottomRight.blockX()) + 1
        val firstMinZ = min(topLeft.blockZ(), bottomRight.blockZ()) + 1
        val firstMaxX = max(topLeft.blockX(), bottomRight.blockX())
        val firstMaxZ = max(topLeft.blockZ(), bottomRight.blockZ())

        player.showParticle(
            Particle.particle(
                type = ParticleType.ENCHANTED_HIT,
                data = OffsetAndSpeed(),
                count = 1,
            ),
            Renderer.fixedRectangle(Vec(firstMinX.toDouble(), topLeft.y(), firstMinZ.toDouble()), Vec(firstMaxX.toDouble(), bottomRight.y(), firstMaxZ.toDouble()), 0.5)
        )
    }

    companion object {
        fun isInside(bound: RoomBounds, point: Point, tolerance: Int = -1): Boolean {
            val firstMinX = min(bound.topLeft.blockX(), bound.bottomRight.blockX())
            val firstMinZ = min(bound.topLeft.blockZ(), bound.bottomRight.blockZ())
            val firstMaxX = max(bound.topLeft.blockX(), bound.bottomRight.blockX())
            val firstMaxZ = max(bound.topLeft.blockZ(), bound.bottomRight.blockZ())

            val sizeX = abs(firstMaxX - firstMinX)
            val sizeZ = abs(firstMaxZ - firstMinZ)

            val thisRect = Rectangle(firstMinX, firstMinZ, sizeX, sizeZ)
            thisRect.grow(tolerance, tolerance)

            return thisRect.contains(point.x(), point.z())
        }

        fun isOverlapping(first: RoomBounds, other: RoomBounds): Boolean {
            val firstMinX = min(first.topLeft.blockX(), first.bottomRight.blockX())
            val firstMinZ = min(first.topLeft.blockZ(), first.bottomRight.blockZ())
            val firstMaxX = max(first.topLeft.blockX(), first.bottomRight.blockX())
            val firstMaxZ = max(first.topLeft.blockZ(), first.bottomRight.blockZ())

            val otherMinX = min(other.topLeft.blockX(), other.bottomRight.blockX())
            val otherMinZ = min(other.topLeft.blockZ(), other.bottomRight.blockZ())
            val otherMaxX = max(other.topLeft.blockX(), other.bottomRight.blockX())
            val otherMaxZ = max(other.topLeft.blockZ(), other.bottomRight.blockZ())

            val sizeX = abs(firstMaxX - firstMinX)
            val sizeZ = abs(firstMaxZ - firstMinZ)

            val otherSizeX = abs(otherMaxX - otherMinX)
            val otherSizeZ = abs(otherMaxZ - otherMinZ)

            val thisRect = Rectangle(firstMinX, firstMinZ, sizeX, sizeZ)
            val otherRect = Rectangle(otherMinX, otherMinZ, otherSizeX, otherSizeZ)

            return thisRect.intersects(otherRect)
        }
    }
}
