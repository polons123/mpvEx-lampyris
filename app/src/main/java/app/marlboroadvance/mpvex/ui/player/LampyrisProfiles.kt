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

    private val allKeys: Set<String> by lazy { PROFILES.values.flatMap { p -> p.map { it.first } }.toSet() }

    /** Before MPVLib.init(): options only, user mpv.conf can still override them. */
    fun applyInitial(name: String) {
        PROFILES[name]?.forEach { (k, v) -> MPVLib.setOptionString(k, v) }
    }

    /** At runtime: back to the baseline (mpv profile [base]) and sets the profile's options. */
    fun applyNow(name: String, base: String) {
        for (k in allKeys) {
            val def = MPVLib.getPropertyString("option-info/$k/default-value") ?: continue
            MPVLib.setPropertyString(k, def)
        }
        MPVLib.command("apply-profile", base)
        PROFILES[name]?.forEach { (k, v) -> MPVLib.setPropertyString(k, v) }
    }

    // SDR auto-correction: gamma/saturation for video that is not PQ/HLG (HDR)
    private const val AUTO_GAMMA = 8
    private const val AUTO_SATURATION = 4
    private var autoApplied = false

    fun isSdr(gamma: String?) = !gamma.isNullOrEmpty() && gamma != "pq" && gamma != "hlg"

    fun onVideoGamma(autoOn: Boolean, gamma: String?) {
        if (!autoOn && !autoApplied) return
        if (autoOn && isSdr(gamma)) {
            MPVLib.setPropertyInt("gamma", AUTO_GAMMA)
            MPVLib.setPropertyInt("saturation", AUTO_SATURATION)
            autoApplied = true
        } else if (autoApplied && !gamma.isNullOrEmpty()) {
            MPVLib.setPropertyInt("gamma", 0)
            MPVLib.setPropertyInt("saturation", 0)
            autoApplied = false
        }
    }
}
