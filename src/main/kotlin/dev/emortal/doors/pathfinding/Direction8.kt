package dev.emortal.doors.pathfinding

enum class Direction8(val offX: Int, val offZ: Int) {
    // couldn't be arsed to name these
    A(0, -1),
    B(0, 1),
    C(-1, -1),
    D(-1, 0),
    E(-1, 1),
    F(1, -1),
    G(1, 0),
    H(1, 1),
}