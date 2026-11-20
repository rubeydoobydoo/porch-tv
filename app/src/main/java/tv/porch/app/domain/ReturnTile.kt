package tv.porch.app.domain

/** Remembers the tile that had focus when the player opened, so coming back lands on it, once. Tiles are told apart by their place on screen. */
class ReturnTile {
    private var last: Int? = null

    var armed = false
        private set

    fun focused(tile: Int) {
        last = tile
    }

    fun leaving() {
        armed = true
    }

    fun isFor(tile: Int): Boolean = armed && tile == last

    fun landed() {
        armed = false
    }
}
