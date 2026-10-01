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

        val geoJson = Json.parseToJsonElement(StationLayers.buildGeoJson(stations)).jsonObject

        assertEquals("FeatureCollection", geoJson["type"]!!.jsonPrimitive.content)
        val features = geoJson["features"]!!.jsonArray.map { it.jsonObject }
        assertEquals(3, features.size)
        val first = features[0]
        assertEquals("3464", first["id"]!!.jsonPrimitive.content)
        val geometry = first["geometry"] as JsonObject
        assertEquals("Point", geometry["type"]!!.jsonPrimitive.content)
        assertEquals("3464", (first["properties"] as JsonObject)["id"]!!.jsonPrimitive.content)
        // GeoJSON order is [longitude, latitude].
        assertEquals(listOf("11.57083", "44.88012"), geometry["coordinates"]!!.jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun emptyList() {
        assertEquals("""{"type":"FeatureCollection","features":[]}""", StationLayers.buildGeoJson(emptyList<Station>()))
    }
}
