package app.marlboroadvance.mpvex.ui.player

import `is`.xyz.mpv.MPVLib

/**
 * Lampyris: built-in picture profiles (same as in the Lampyris client and the mpv-profiles repository)
 * and SDR auto-correction. Profiles are option lists, because libmpv can't define profiles at runtime.
 */
object LampyrisProfiles {
    val PROFILES: Map<String, List<Pair<String, String>>> = linkedMapOf(
        "balanced" to listOf(
            "scale" to "ewa_lanczossharp",
            "cscale" to "ewa_lanczossharp",
            "dscale" to "mitchell",
            "scale-antiring" to "0.6",
            "correct-downscaling" to "yes",
            "linear-downscaling" to "yes",
            "sigmoid-upscaling" to "yes",
            "deband" to "yes",
            "deband-iterations" to "2",
            "deband-threshold" to "35",
            "deband-range" to "20",
            "deband-grain" to "5"
        ),
        "quality" to listOf(
            "scale" to "ewa_lanczossharp",
            "cscale" to "ewa_lanczossharp",
            "dscale" to "ewa_lanczos",
            "scale-antiring" to "0.6",
            "cscale-antiring" to "0.6",
            "correct-downscaling" to "yes",
            "linear-downscaling" to "yes",
            "sigmoid-upscaling" to "yes",
            "deband" to "yes",
            "deband-iterations" to "4",
            "deband-threshold" to "40",
            "deband-range" to "20",
            "deband-grain" to "8",
            "hdr-compute-peak" to "yes",
            "tone-mapping" to "bt.2390"
        ),
        "fast" to listOf(
            "scale" to "bilinear",
            "cscale" to "bilinear",
            "dscale" to "bilinear",
            "deband" to "no",
            "dither-depth" to "no",
            "correct-downscaling" to "no",
            "sigmoid-upscaling" to "no"
        ),
        "hdr-sdr" to listOf(
            "tone-mapping" to "bt.2390",
            "hdr-compute-peak" to "yes",
            "target-peak" to "auto",
            "deband" to "no"
        ),
        "smooth" to listOf(
            "video-sync" to "display-resample",
            "interpolation" to "yes",
            "tscale" to "oversample"
        ),
        "anime" to listOf(
            "scale" to "ewa_lanczossharp",
            "cscale" to "ewa_lanczossharp",
            "sigmoid-upscaling" to "yes",
            "deband" to "yes",
            "deband-iterations" to "3",
            "deband-threshold" to "48",
            "deband-range" to "16",
            "deband-grain" to "0"
        ),
        "night" to listOf(
            "af" to "acompressor=ratio=4,loudnorm=I=-16:TP=-1.5:LRA=11"
        )
    )

    val NAMES: List<String> get() = PROFILES.keys.toList()

    private val ownKeys: Set<String> by lazy { PROFILES.values.flatMap { p -> p.map { it.first } }.toSet() }

    /** Options changed by built-in mpv profiles (fast, high-quality, ...), read from the "profile-list" property. */
    private fun builtinKeys(): Set<String> = runCatching {
        val keys = mutableSetOf<String>()
        val list = org.json.JSONArray(MPVLib.getPropertyString("profile-list") ?: "[]")
        for (i in 0 until list.length()) {
            val profile = list.getJSONObject(i)
            if (profile.optString("name") == "default") continue
            val options = profile.optJSONArray("options") ?: continue
            for (j in 0 until options.length()) keys.add(options.getJSONObject(j).optString("key"))
        }
        keys.remove("")
        keys
    }.getOrDefault(emptySet())

    /** Before MPVLib.init(): options only, user mpv.conf can still override them. */
    fun applyInitial(name: String) {
        PROFILES[name]?.forEach { (k, v) -> MPVLib.setOptionString(k, v) }
    }

    /**
     * At runtime: resets everything that profiles can change to defaults, applies the base mpv profile
     * ([base]: fast, high-quality, ...), then the picture profile [name] on top.
     */
    fun applyNow(name: String, base: String) {
        for (k in ownKeys + builtinKeys()) {
            val def = MPVLib.getPropertyString("option-info/$k/default-value") ?: continue
            MPVLib.setPropertyString(k, def)
        }
        MPVLib.command("apply-profile", base)
        PROFILES[name]?.forEach { (k, v) -> MPVLib.setPropertyString(k, v) }
    }

    // SDR auto-correction: added on top of the user's filter values (gamma +8, saturation +4) for video that is not PQ/HLG (HDR)
    private val AUTO_OFFSETS = mapOf("gamma" to 8, "saturation" to 4)
    private var autoApplied = false

    fun isSdr(gamma: String?) = !gamma.isNullOrEmpty() && gamma != "pq" && gamma != "hlg"

    private fun clamp(v: Int) = v.coerceIn(-100, 100)

    private fun offset(property: String) = if (autoApplied) AUTO_OFFSETS[property] ?: 0 else 0

    /** Sets a filter (brightness, gamma, ...) to the user's value plus the active auto-correction offset. */
    fun setFilter(property: String, userValue: Int) {
        MPVLib.setPropertyInt(property, clamp(userValue + offset(property)))
    }

    /** [userValue] returns the user's saved filter value for an mpv property. */
    fun onVideoGamma(autoOn: Boolean, gamma: String?, userValue: (String) -> Int) {
        if (!autoOn && !autoApplied) return
        if (autoOn && isSdr(gamma)) {
            autoApplied = true
        } else if (autoApplied && !gamma.isNullOrEmpty()) {
            autoApplied = false
        } else {
            return
        }
        for (property in AUTO_OFFSETS.keys) setFilter(property, userValue(property))
    }
}
