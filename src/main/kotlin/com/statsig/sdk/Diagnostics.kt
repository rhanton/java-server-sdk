package com.statsig.sdk

import okhttp3.Response

const val NANO_IN_MS = 1_000_000.0
const val MAX_SAMPLING_RATE = 10_000
internal class Diagnostics(private var isDisabled: Boolean, private var logger: StatsigLogger) {
    var diagnosticsContext: ContextType = ContextType.INITIALIZE
    private val samplingRates: MutableMap<String, Int> = mutableMapOf(
        "dcs" to 0,
        "log" to 0,
        "initialize" to MAX_SAMPLING_RATE,
        "idlist" to 0,
        "api_call" to 0,
    )
    internal var markers: DiagnosticsMarkers = mutableMapOf()

    fun setSamplingRate(rates: Map<String, Int>) {
        rates.forEach { entry ->
            if (samplingRates.containsKey(entry.key)) {
                val rate = entry.value
                val samplingRate = if (rate in 0..MAX_SAMPLING_RATE) {
                    rate
                } else {
                    if (rate < 0) 0 else MAX_SAMPLING_RATE
                }
                samplingRates[entry.key] = samplingRate
            }
        }
    }

    fun markStart(key: KeyType, step: StepType? = null, context: ContextType? = null, additionalMarker: Marker? = null) {
        if (isDisabled) {
            return
        }
        val contextType = context ?: diagnosticsContext
        val marker = Marker(key = key, action = ActionType.START, timestamp = System.nanoTime() / NANO_IN_MS, step = step)
        when (key) {
            KeyType.GET_ID_LIST -> {
                marker.url = additionalMarker?.url!!
            }
            KeyType.GET_ID_LIST_SOURCES -> {
                if (step == StepType.PROCESS) {
                    marker.idListCount = additionalMarker?.idListCount!!
                }
            }
        }
        when (contextType) {
            ContextType.API_CALL -> {
                marker.configName = additionalMarker?.configName
                marker.markerID = additionalMarker?.markerID
            }
        }
        this.addMarker(marker, contextType)
    }

    fun markEnd(key: KeyType, success: Boolean, step: StepType? = null, context: ContextType? = null, additionalMarker: Marker? = null) {
        if (isDisabled) {
            return
        }
        val contextType = context ?: diagnosticsContext
        val marker = Marker(key = key, action = ActionType.END, success = success, timestamp = System.nanoTime() / NANO_IN_MS, step = step)
        when (key) {
            KeyType.DOWNLOAD_CONFIG_SPECS -> {
                if (step == StepType.NETWORK_REQUEST) {
                    marker.sdkRegion = additionalMarker?.sdkRegion
                    marker.statusCode = additionalMarker?.statusCode
                }
            }
            KeyType.GET_ID_LIST -> {
                if (step == StepType.NETWORK_REQUEST) {
                    marker.url = additionalMarker?.url!!
                    marker.sdkRegion = additionalMarker?.sdkRegion
                    marker.statusCode = additionalMarker?.statusCode
                } else if (step == StepType.PROCESS) {
                    marker.url = additionalMarker?.url!!
                }
            }
            KeyType.GET_ID_LIST_SOURCES -> {
                if (step == StepType.NETWORK_REQUEST) {
                    marker.sdkRegion = additionalMarker?.sdkRegion
                    marker.statusCode = additionalMarker?.statusCode
                }
            }
            KeyType.OVERALL -> {
                marker.reason = additionalMarker?.reason
            }
        }
        when (contextType) {
            ContextType.API_CALL -> {
                marker.configName = additionalMarker?.configName
                marker.markerID = additionalMarker?.markerID
            }
        }
        this.addMarker(marker, contextType)
    }

    internal fun shouldLogDiagnostics(context: ContextType): Boolean {
        val samplingKey: String =
            when (context) {
                ContextType.CONFIG_SYNC -> "dcs"
                ContextType.INITIALIZE -> "initialize"
                ContextType.API_CALL -> "api_call"
            }
        val rand = Math.random() * MAX_SAMPLING_RATE
        return samplingRates[samplingKey] ?: 0 > rand
    }

    private fun addMarker(marker: Marker, context: ContextType) {
        if (this.markers[context] == null) {
            this.markers[context] = mutableListOf()
        }
        this.markers[context]?.add(marker)
        this.markers.values
    }

    fun logDiagnostics(context: ContextType) {
        if ((markers[context]?.size ?: 0) <= 0 || !shouldLogDiagnostics(context)) {
            return
        }
        logger.logDiagnostics(context, markers[context]!!)
        markers[context] = mutableListOf()
    }

    fun startNetworkRequestDiagnostics(key: KeyType?) {
        if (key == null) {
            return
        }
        this.markStart(key, step = StepType.NETWORK_REQUEST)
    }

    fun endNetworkRequestDiagnostics(key: KeyType?, success: Boolean, response: Response?) {
        if (key == null) {
            return
        }
        val marker = if (response != null) Marker(sdkRegion = response.headers["x-statsig-region"], statusCode = response.code) else null
        this.markEnd(key, success, StepType.NETWORK_REQUEST, additionalMarker = marker)
    }

    fun getDiagnosticKeyFromURL(url: String): KeyType? {
        if (url.contains("/download_config_specs")) {
            return KeyType.DOWNLOAD_CONFIG_SPECS
        }
        if (url.contains("/get_id_lists")) {
            return KeyType.GET_ID_LIST_SOURCES
        }
        return null
    }
}
