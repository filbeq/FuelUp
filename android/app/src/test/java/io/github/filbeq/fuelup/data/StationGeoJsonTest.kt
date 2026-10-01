package io.github.filbeq.fuelup.data

import io.github.filbeq.fuelup.map.StationLayers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class StationGeoJsonTest {
    @Test
    fun onePointPerStationWithIdAndLonLat() {
        val stations = checkNotNull(javaClass.getResourceAsStream("/fixtures/stations.json"))
            .use(StationDataJson::parseStations).stations

        val file = checkNotNull(javaClass.getResourceAsStream("/fixtures/stations.json")).use(StationDataJson::parseStations)
        val petrolSelf = FuelChoice.Default
        val indices = file.standardFuelIndices(petrolSelf.fuel)
        val geoJson = Json.parseToJsonElement(
            StationLayers.buildGeoJson(stations) { it.priceFor(petrolSelf, indices) },
        ).jsonObject

        assertEquals("FeatureCollection", geoJson["type"]!!.jsonPrimitive.content)
        val features = geoJson["features"]!!.jsonArray.map { it.jsonObject }
        // 40820 sells no petrol: left out.
        assertEquals(listOf("3464", "54386"), features.map { it["id"]!!.jsonPrimitive.content })
        val first = features[0]
        assertEquals("3464", first["id"]!!.jsonPrimitive.content)
        val geometry = first["geometry"] as JsonObject
        assertEquals("Point", geometry["type"]!!.jsonPrimitive.content)
        val properties = first["properties"] as JsonObject
        assertEquals("3464", properties["id"]!!.jsonPrimitive.content)
        assertEquals("2049", properties["p"]!!.jsonPrimitive.content) // Benzina self
        // GeoJSON order is [longitude, latitude].
        assertEquals(listOf("11.57083", "44.88012"), geometry["coordinates"]!!.jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun emptyList() {
        assertEquals("""{"type":"FeatureCollection","features":[]}""", StationLayers.buildGeoJson(emptyList<Station>()) { null })
    }
}
