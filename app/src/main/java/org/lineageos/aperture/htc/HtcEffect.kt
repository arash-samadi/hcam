/*
 * SPDX-FileCopyrightText: The hcam project
 * SPDX-License-Identifier: Apache-2.0
 *
 * HTC Inspire 4G (Sense 2.1) effect definitions, recovered from
 * libposteffect.so and HTCCamera.apk. Do not "modernise" these values:
 * ids, tags and parameter meanings are part of the original behaviour.
 */

package org.lineageos.aperture.htc

/** Effect ids as parsed by GECameraParameters::getGPUEffect() (first ASCII digit). */
enum class HtcEffect(val id: Int, val tag: String, val label: String) {
    NONE(0, "bypass", "No effect"),
    VINTAGE(1, "lomo", "Vintage"),
    DEPTH_OF_FIELD(2, "dof", "Depth of field"),
    DISTORTION(3, "distortion", "Distortion"),
    VIGNETTE(4, "spotlight", "Vignette"),
    DOTS(5, "dots", "Dots"),
    CONTRAST(6, "contrast", "Contrast"),
    TILT_SHIFT(7, "ts", "Tilt-shift"),
    MIRROR(8, "mirror", "Mirror"),
    BLOOM(9, "bloom", "Bloom");

    companion object {
        fun fromId(id: Int): HtcEffect = entries.first { it.id == id }
    }
}

/**
 * Vintage is one effect id (1_lomo) with four variants selected by GE-param0.
 * Curve/mask pairing is from GEShaderVintage::SetParam (confirmed).
 */
enum class VintageVariant(
    val param0: Int,
    val curveAsset: String,
    val vignetteAsset: String,
) {
    CROSS(0, "CurveCross.png", "mask_vignette_wide.png"),
    COLD(1, "CurveCold.png", "mask_vignette_wide.png"),
    WARM(2, "CurveWarm.png", "mask_vignette_wide.png"),
    SEPIA(3, "CurveSepia.png", "mask_vignette_legacy.png"),
}

/**
 * One GE-param0/GE-param1 pair as sent by CameraThread.
 * x/y/w/h are in preview-surface pixels, exactly like the original app;
 * the engine normalises them per frame in the same way as GEShaderDoF::SetParam.
 */
data class HtcEffectParams(
    val param0: Int,
    val x: Int = 0,
    val y: Int = 0,
    val w: Int = 0,
    val h: Int = 0,
    val moving: Boolean = false,
)

/** A single GL program: vertex + fragment source file names. */
data class HtcPass(val vertex: String, val fragment: String)

/**
 * Program sets per effect and size class, from SHADER-MAP.md.
 * "large" is the capture path used for stills above the original GL size limit.
 * The exact threshold is inherited from GEManager; see FINDINGS.md §6.
 */
object HtcPrograms {
    private fun p(v: String, f: String) = HtcPass(v, f)

    private val VERTEX_COMMON = "20_vertex_022dea.glsl"

    fun passes(effect: HtcEffect, large: Boolean): List<HtcPass> = when (effect) {
        HtcEffect.NONE -> listOf(p(VERTEX_COMMON, "21_fragment_022f00.glsl"))

        HtcEffect.VINTAGE -> if (large)
            listOf(p("05_vertex_01fd85.glsl", "06_fragment_01ff4a.glsl"))
        else
            listOf(p("03_vertex_01f8bc.glsl", "04_fragment_01fa13.glsl"))

        HtcEffect.DEPTH_OF_FIELD -> listOf(
            p("07_vertex_0205fc.glsl", "08_fragment_0206a8.glsl"),
            if (large) p("09_vertex_020fad.glsl", "10_fragment_02116d.glsl")
            else p("11_vertex_021501.glsl", "12_fragment_02164d.glsl"),
        )

        HtcEffect.DISTORTION -> listOf(
            if (large) p("17_vertex_022472.glsl", "18_fragment_022552.glsl")
            else p("17_vertex_022472.glsl", "19_fragment_0229cb.glsl"),
        )

        HtcEffect.VIGNETTE -> listOf(
            if (large) p("13_vertex_021b81.glsl", "14_fragment_021d27.glsl")
            else p("15_vertex_021fcf.glsl", "16_fragment_0220de.glsl"),
        )

        HtcEffect.DOTS -> if (large) listOf(
            p("15_vertex_021fcf.glsl", "26_fragment_023a39.glsl"),
            p("23_vertex_0235b4.glsl", "24_fragment_02373a.glsl"),
            p(VERTEX_COMMON, "22_fragment_02345f.glsl"),
        ) else listOf(
            p("15_vertex_021fcf.glsl", "27_fragment_023bae.glsl"),
            p("23_vertex_0235b4.glsl", "25_fragment_0238a8.glsl"),
            // No small DotPass3 exists in the binary; the large pass 3 is the only one.
            p(VERTEX_COMMON, "22_fragment_02345f.glsl"),
        )

        HtcEffect.CONTRAST -> listOf(
            if (large) p("05_vertex_01fd85.glsl", "30_fragment_02421f.glsl")
            else p("28_vertex_023d29.glsl", "29_fragment_023e57.glsl"),
        )

        HtcEffect.TILT_SHIFT -> listOf(
            p("07_vertex_0205fc.glsl", "08_fragment_0206a8.glsl"),
            if (large) p("09_vertex_020fad.glsl", "31_fragment_0246d5.glsl")
            else p("11_vertex_021501.glsl", "32_fragment_024ac2.glsl"),
        )

        HtcEffect.MIRROR -> if (large) listOf(
            p("33_vertex_024e7c.glsl", "34_fragment_025021.glsl"),
            p("33_vertex_024e7c.glsl", "35_fragment_025212.glsl"),
        ) else listOf(
            p(VERTEX_COMMON, "36_fragment_025403.glsl"),
            p(VERTEX_COMMON, "37_fragment_0255d4.glsl"),
        )

        HtcEffect.BLOOM -> listOf(
            if (large) p("33_vertex_024e7c.glsl", "38_fragment_0257e7.glsl")
            else p(VERTEX_COMMON, "38_fragment_0257e7.glsl"),
        )
    }
}
