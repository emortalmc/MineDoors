package dev.emortal.doors.pathfinding

import net.minestom.server.coordinate.Point
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block

class RushPathfinding(val instance: Instance) {

    fun heuristic(point: Point, target: Point): Double = point.distanceSquared(target)
    fun heuristic(node: PathNode, target: PathNode): Double {
        return node.pos.distanceSquared(target.pos) + if (node.directionToParent != target.directionToParent) 1000 else 0
    }

    fun neighbours(point: Point) = Directions.values().map { point.add(it.offset()) to it }.filter {
//        if (instance.getChunkAt(it.first) == null) {
//            return@filter false
//        }
        // Check for unloaded chunks
        val block: Block
        try {
            block = instance.getBlock(it.first)
        } catch (e: NullPointerException) {
            return@filter false
        }
        val blockAbove = instance.getBlock(it.first.add(0.0, 1.0, 0.0))
        (!block.isSolid || block.compare(Block.IRON_DOOR)) && (!blockAbove.isSolid || blockAbove.compare(Block.IRON_DOOR))
    }.map { PathNode(it.first) to it.second }

//    fun neighboursDiag(point: Point) = Direction8.values().map { point.add(it.offX.toDouble(), 0.0, it.offZ.toDouble()) }.filter {
//        val block = instance.getBlock(it)
//        val blockAbove = instance.getBlock(it.add(0.0, 1.0, 0.0))
//        (block.isAir || block.compare(Block.IRON_DOOR)) && (blockAbove.isAir || blockAbove.compare(Block.IRON_DOOR))
//    }.map { PathNode(it) }

    fun pathfind(start: Point, target: Point): Set<Point>? {
        val startNode = PathNode(start, g = 0.0, h = start.distanceSquared(target))

        val openList = mutableSetOf<PathNode>(startNode)
        val closedList = mutableSetOf<Point>()

        var iteration = 0

        while (openList.isNotEmpty() && iteration < 500) {

            iteration++

            val current = openList.minBy { it.f }
            openList.remove(current)
            closedList.add(current.pos)

            if (current.pos == target) {
                // Found target!

                var currentPathNode = current
                val path = mutableSetOf<Point>()
                while(currentPathNode.pos != start) {
                    path.add(currentPathNode.pos)
                    currentPathNode = currentPathNode.parent!!
                }

                return path
            }

            neighbours(current.pos).filter { !closedList.contains(it.first.pos) }.forEach { neighbour ->
                val inSearch = openList.any { it.pos == neighbour.first.pos }
                val gCostToNeighbour = current.g + current.pos.distanceSquared(neighbour.first.pos) + if (current.directionToParent != neighbour.second) Double.MAX_VALUE / 3.0  else 0.0

                if (!inSearch || gCostToNeighbour < neighbour.first.g) {
                    neighbour.first.g = gCostToNeighbour
                    neighbour.first.parent = current
                    neighbour.first.directionToParent = neighbour.second

                    if (!inSearch) {
                        neighbour.first.h = neighbour.first.pos.distanceSquared(target)
                        openList.add(neighbour.first)
                    }
                }
            }
        }

//        val batch = AbsoluteBlockBatch()
//        openList.forEach {
//            batch.setBlock(it.pos, Block.DIAMOND_BLOCK)
//        }
//        closedList.forEach {
//            batch.setBlock(it, Block.REDSTONE_BLOCK)
//        }
//
//        batch.apply(instance, null)

        return null
    }

}