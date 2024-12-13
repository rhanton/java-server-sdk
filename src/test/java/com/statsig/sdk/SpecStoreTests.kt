package com.statsig.sdk

import com.statsig.sdk.StatsigServer.Companion.create
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class SpecStoreTests {

    lateinit var driver: StatsigServer

    @Before
    fun setup() {
        driver = create()
        driver.initializeAsync("secret-local", StatsigOptions()).get()
    }

    @Test
    fun setDownloadedConfigsTest() {
        var specStore = TestUtilJava.getSpecStoreFromStatsigServer(driver)

        assertNull(specStore.getConfig("dynamicConfig"))
        assert(specStore.getAllGates() == emptyMap<String, String>())
        assert(specStore.getAllLayers() == emptyMap<String, String>())
        assert(specStore.getLastUpdateTime() == 0L)

        var input = APIDownloadedConfigs(
            dynamicConfigs = arrayOf(createAPIConfig("dynamicConfig")),
            featureGates = arrayOf(createAPIConfig("featureGate")),
            layerConfigs = arrayOf(createAPIConfig("layer")),
            idLists = emptyMap(),
            layers = mapOf("layer" to arrayOf("experiment1", "experiment2")),
            time = 420,
            hasUpdates = true,
        )

        specStore.setDownloadedConfigs(input)
        assertNotNull(specStore.getConfig("dynamicConfig"))
        assertNotNull(specStore.getGate("featureGate"))
        assertNotNull(specStore.getLayerConfig("layer"))
        assertEquals("layer", specStore.getLayerNameForExperiment("experiment1"))
        assertEquals("layer", specStore.getLayerNameForExperiment("experiment2"))
        assertEquals(420, specStore.getLastUpdateTime())
    }

    private fun createAPIConfig(name: String): APIConfig {
        return APIConfig(
            name,
            "",
            false,
            "",
            "",
            false,
            emptyArray(),
            "",
            "",
            null,
            null,
            forwardAllExposures = null,
        )
    }
}
