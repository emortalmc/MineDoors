package dev.emortal.doors.raycast

import dev.emortal.rayfast.area.area3d.Area3d
import dev.emortal.rayfast.area.area3d.Area3dRectangularPrism
import dev.emortal.rayfast.casting.grid.GridCast
import dev.emortal.rayfast.vector.Vector3d
import net.minestom.server.collision.BoundingBox
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import world.cepi.kstom.util.component1
import world.cepi.kstom.util.component2
import world.cepi.kstom.util.component3

object RaycastUtil {

    private val boundingBoxToArea3dMap = HashMap<BoundingBox, Area3d>()
    private const val tolerance: Double = 0.1

    private val blockBlacklist = setOf(
        Block.IRON_DOOR,
        Block.OAK_DOOR,
        Block.SPRUCE_DOOR,
        Block.SPRUCE_TRAPDOOR
    )


    init {
        Area3d.CONVERTER.register(BoundingBox::class.java) { box ->
            boundingBoxToArea3dMap.computeIfAbsent(box) { it ->
                Area3dRectangularPrism.wrapper(
                    it,
                    { it.minX() - tolerance }, { it.minY() - tolerance }, { it.minZ() - tolerance },
                    { it.maxX() + tolerance }, { it.maxY() + tolerance }, { it.maxZ() + tolerance }
                )
            }

            boundingBoxToArea3dMap[box]
        }
    }


    val Entity.area3d: Area3d
        get() = Area3d.CONVERTER.from(boundingBox)

//    fun Entity.fastHasLineOfSight(entity: Entity): Boolean {
//        val (x, y, z) = this
//
//        val direction = this.position.asVec().sub(entity.position.asVec()).normalize()
//
//        return this.area3d.lineIntersects(
//            x, y, z,
//            direction.x(), direction.y(), direction.z()
//        )
//    }

    fun Entity.fastHasLineOfSight(entity: Entity): Boolean {
        val (x, y, z) = this

        val direction = this.position.asVec().sub(entity.position.asVec()).normalize()

        return this.area3d.lineIntersects(
            x, y, z,
            direction.x(), direction.y(), direction.z()
        )
    }

    @Suppress("INACCESSIBLE_TYPE")
    fun raycastBlock(instance: Instance, startPoint: Point, direction: Vec, maxDistance: Double): Pos? {
        val gridIterator: Iterator<Vector3d> = GridCast.createExactGridIterator(
            startPoint.x(), startPoint.y(), startPoint.z(),
            direction.x(), direction.y(), direction.z(),
            1.0, maxDistance
        )

        while (gridIterator.hasNext()) {
            val gridUnit = gridIterator.next()
            val pos = Pos(gridUnit[0], gridUnit[1], gridUnit[2])

            try {
                val hitBlock = instance.getBlock(pos)

                if (hitBlock.isSolid) {
                    return pos
                }
            } catch (e: NullPointerException) {
                // catch if chunk is not loaded
                break
            }
        }

        return null
    }

    fun raycastEntity(
        instance: Instance,
        startPoint: Point,
        direction: Vec,
        maxDistance: Double,
        hitFilter: (Entity) -> Boolean = { true }
    ): Pair<Entity, Pos>? {
        
        instance.entities
            .filter { hitFilter.invoke(it) }
            .filter { it.position.distanceSquared(startPoint) <= maxDistance * maxDistance }
            .forEach {
                val area = it.area3d
                val pos = it.position

                //val intersection = it.boundingBox.boundingBoxRayIntersectionCheck(startPoint.asVec(), direction, it.position)

                val intersection = area.lineIntersection(
                    Vector3d.of(startPoint.x() - pos.x, startPoint.y() - pos.y, startPoint.z() - pos.z),
                    Vector3d.of(direction.x(), direction.y(), direction.z())
                )
                if (intersection != null) {
                    return Pair(it, Pos(intersection[0] + pos.x, intersection[1] + pos.y, intersection[2] + pos.z))
                }
            }

        return null
    }

    fun raycast(
        instance: Instance,
        startPoint: Point,
        direction: Vec,
        maxDistance: Double,
        hitFilter: (Entity) -> Boolean = { true }
    ): RaycastResult {
        val blockRaycast = raycastBlock(instance, startPoint, direction, maxDistance)
        val entityRaycast = raycastEntity(instance, startPoint, direction, maxDistance, hitFilter)



        if (entityRaycast == null && blockRaycast == null) {
            return RaycastResult(RaycastResultType.HIT_NOTHING, null, null)
        }

        if (entityRaycast == null && blockRaycast != null) {
            return RaycastResult(RaycastResultType.HIT_BLOCK, null, blockRaycast)
        }

        if (entityRaycast != null && blockRaycast == null) {
            return RaycastResult(RaycastResultType.HIT_ENTITY, entityRaycast.first, entityRaycast.second)
        }

        // Both entity and block check have collided, time to see which is closer!

        val distanceFromEntity = startPoint.distanceSquared(entityRaycast!!.second)
        val distanceFromBlock = startPoint.distanceSquared(blockRaycast!!)

        return if (distanceFromBlock > distanceFromEntity) {
            RaycastResult(RaycastResultType.HIT_ENTITY, entityRaycast.first, entityRaycast.second)
        } else {
            RaycastResult(RaycastResultType.HIT_BLOCK, null, blockRaycast)
        }

    }

}