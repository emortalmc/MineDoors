package dev.emortal.doors.lobby

import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.metadata.other.ArmorStandMeta

class SeatEntity(physics: Boolean = false, entityType: EntityType = EntityType.ARMOR_STAND, val onRemove: (SeatEntity) -> Unit) : Entity(entityType) {

    init {
        if (entityType == EntityType.ARMOR_STAND) {
            val meta = entityMeta as ArmorStandMeta
            meta.setNotifyAboutChanges(false)
            meta.isMarker = true
            meta.isInvisible = true
            meta.isHasNoBasePlate = true
            meta.isSmall = true
            meta.setNotifyAboutChanges(true)
        }

        setNoGravity(true)
        hasPhysics = physics
    }

    override fun removePassenger(entity: Entity) {
        super.removePassenger(entity)

        entity.velocity = Vec(0.0, 10.0, 0.0)

        if (passengers.isEmpty()) {
            onRemove(this)
            remove()
        }
    }

}