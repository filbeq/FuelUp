package io.github.filbeq.fuelup.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoritesTest {
    private fun station(id: Int, name: String = "S$id", lat: Double = 45.0 + id / 100.0, lon: Double = 9.0) =
        Station(id, name, 0, 0, "VIA X", "PAVIA", "PV", lat, lon, emptyList())

    private fun file(date: String, vararg stations: Station) =
        StationsFile(1, date, listOf("Eni"), emptyList(), stations.sortedBy { it.id })

    private val day1 = file("2026-10-01", station(1), station(2), station(3))

    @Test
    fun toggleAddsAtTopAndRemoves() {
        var list = Favorites.toggle(emptyList(), station(1), day1)
        list = Favorites.toggle(list, station(2), day1)
        assertEquals(listOf(2, 1), list.map { it.id })
        assertEquals("Eni", list[0].brand)
        assertEquals("2026-10-01", list[0].lastSeen)
        list = Favorites.toggle(list, station(2), day1)
        assertEquals(listOf(1), list.map { it.id })
    }

    @Test
    fun encodeDecodeRoundTrip() {
        val list = Favorites.toggle(Favorites.toggle(emptyList(), station(1), day1), station(3), day1)
        assertEquals(list, FavoritesStore.decode(FavoritesStore.encode(list)))
    }

    @Test
    fun unreadableTextGivesEmptyList() {
        assertEquals(emptyList<FavoriteStation>(), FavoritesStore.decode(null))
        assertEquals(emptyList<FavoriteStation>(), FavoritesStore.decode("not json"))
        assertEquals(emptyList<FavoriteStation>(), FavoritesStore.decode("""[{"id":"x"}]"""))
    }

    @Test
    fun refreshUpdatesPresentAndKeepsMissing() {
        val list = Favorites.toggle(Favorites.toggle(emptyList(), station(1), day1), station(2), day1)
        // Next day: station 1 renamed, station 2 gone.
        val day2 = file("2026-10-02", station(1, name = "NEW NAME"), station(3))
        val refreshed = Favorites.refresh(list, day2)
        assertEquals(listOf(2, 1), refreshed.map { it.id })
        val missing = refreshed[0]
        assertEquals("S2", missing.name)
        assertEquals("2026-10-01", missing.lastSeen)
        assertTrue(Favorites.isMissing(missing, day2))
        val present = refreshed[1]
        assertEquals("NEW NAME", present.name)
        assertEquals("2026-10-02", present.lastSeen)
        assertFalse(Favorites.isMissing(present, day2))
    }

    @Test
    fun successorIsAStationAtTheSameCoordinates() {
        val favorite = Favorites.of(station(2), day1)
        // Station 2 registered again as 7, same spot; 8 is elsewhere.
        val day2 = file("2026-10-02", station(1), station(7, name = "OTHER", lat = favorite.lat), station(8))
        assertEquals(7, Favorites.successor(favorite, day2)?.id)
    }

    @Test
    fun successorPrefersTheSameNameAtASharedSpot() {
        val favorite = Favorites.of(station(2), day1)
        val day2 = file(
            "2026-10-02",
            station(5, name = "DIESEL AREA", lat = favorite.lat),
            station(6, name = "S2", lat = favorite.lat),
        )
        assertEquals(6, Favorites.successor(favorite, day2)?.id)
    }

    @Test
    fun noSuccessorElsewhereOrForItself() {
        val favorite = Favorites.of(station(2), day1)
        assertNull(Favorites.successor(favorite, file("2026-10-02", station(1), station(3))))
        // Still present: the station itself is not its own successor.
        assertNull(Favorites.successor(favorite, day1))
    }

    @Test
    fun replaceKeepsThePlaceInTheList() {
        val list = listOf(1, 2, 3).fold(emptyList<FavoriteStation>()) { acc, id -> Favorites.toggle(acc, station(id), day1) }
        assertEquals(listOf(3, 2, 1), list.map { it.id })
        val day2 = file("2026-10-02", station(1), station(3), station(9, lat = list[1].lat))
        val replaced = Favorites.replace(list, list[1], station(9, lat = list[1].lat), day2)
        assertEquals(listOf(3, 9, 1), replaced.map { it.id })
        assertEquals("2026-10-02", replaced[1].lastSeen)
    }

    @Test
    fun replaceWithAnExistingFavoriteLeavesNoDuplicate() {
        val list = listOf(1, 2).fold(emptyList<FavoriteStation>()) { acc, id -> Favorites.toggle(acc, station(id), day1) }
        val replaced = Favorites.replace(list, list[0], station(1), day1)
        assertEquals(listOf(1), replaced.map { it.id })
    }
}
