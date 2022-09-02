package dev.emortal.doors.pathfinding

enum class Directions(val offX: Int, val offY: Int, val offZ: Int) {
    NORTH(0, 0, -1),
    EAST(1, 0, 0),
    SOUTH(0, 0, 1),
    WEST(-1, 0, 0),

//    NORTHUP(0, 1, -1),
//    SOUTHUP(0, 1, 1),
//    WESTUP(-1, 1, 0),
//    EASTUP(1, 1, 0),
//
//    NORTHDOWN(0, -1, -1),
//    SOUTHDOWN(0, -1, 1),
//    WESTDOWN(-1, -1, 0),
//    EASTDOWN(1, -1, 0)
}