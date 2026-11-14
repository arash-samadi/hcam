/*
 * SPDX-FileCopyrightText: The hcam project
 * SPDX-License-Identifier: Apache-2.0
 *
 * Debug-only self test: full-res YUV capture -> HTC engine -> JPEG files.
 * Launch: adb shell am start -n org.hcam.dev/org.lineageos.aperture.htc.HtcSelfTestActivity
 */

package org.lineageos.aperture.htc

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.Image
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.WindowManager
import android.widget.TextView
import java.io.File
import java.util.concurrent.Executors

class HtcSelfTestActivity : Activity() {
    private lateinit var status: TextView
    private val worker = Executors.newSingleThreadExecutor()
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        status = TextView(this).apply {
            textSize = 14f
            setPadding(48, 48, 48, 48)
            text = "hcam self test\nwaiting for camera permission..."
        }
        setContentView(status)
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startTest()
        } else {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 1)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startTest()
        } else {
            setStatus("camera permission denied")
        }
    }

    private fun setStatus(text: String) {
        Log.i(TAG, text.replace('\n', ' '))
        runOnUiThread { status.text = text }
    }

    private fun startTest() {
        thread = HandlerThread("hcam-camera").also { it.start() }
        handler = Handler(thread!!.looper)
        // Deterministic engine validation first: it must work even if the
        // camera cannot be opened (background/locked device, etc.).
        runSynthetic()
        val manager = getSystemService(CameraManager::class.java)
        val id = manager.cameraIdList.firstOrNull { camId ->
            manager.getCameraCharacteristics(camId)
                .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        } ?: manager.cameraIdList.firstOrNull()
        if (id == null) {
            setStatus("no camera")
            return
        }
        val chars = manager.getCameraCharacteristics(id)
        val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        if (map == null) {
            setStatus("no stream configuration map")
            return
        }
        val size = map.getOutputSizes(ImageFormat.YUV_420_888)
            .maxByOrNull { it.width * it.height }
        if (size == null) {
            setStatus("no YUV_420_888 size")
            return
        }
        setStatus("camera $id\n${size.width}x${size.height}")

        reader = ImageReader.newInstance(size.width, size.height, ImageFormat.YUV_420_888, 2)
        reader!!.setOnImageAvailableListener({ r ->
            val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
            process(image)
        }, handler)

        try {
            manager.openCamera(id, object : CameraDevice.StateCallback() {
            override fun onOpened(device: CameraDevice) {
                camera = device
                val surface = reader!!.surface
                device.createCaptureSession(
                    listOf(surface),
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(s: CameraCaptureSession) {
                            session = s
                            val request = device
                                .createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                                .apply {
                                    addTarget(surface)
                                    set(
                                        CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                                    )
                                }
                                .build()
                            s.capture(request, null, handler)
                        }

                        override fun onConfigureFailed(s: CameraCaptureSession) {
                            setStatus("capture session failed")
                        }
                    },
                    handler
                )
            }

            override fun onDisconnected(device: CameraDevice) {
                setStatus("camera disconnected")
            }

            override fun onError(device: CameraDevice, error: Int) {
                setStatus("camera error $error")
            }
        }, handler)
        } catch (t: Throwable) {
            Log.e(TAG, "openCamera failed", t)
            setStatus("openCamera failed: ${t.message}")
        }
    }

    /** Deterministic colour bars + grey ramp; validates the GL engine without a camera. */
    private fun runSynthetic() {
        worker.execute {
            val dir = File(getExternalFilesDir(null), "selftest").apply { mkdirs() }
            val processor = HtcProcessor(this)
            try {
                val sw = 1024
                val sh = 768
                val bars = intArrayOf(
                    0xFFFFFF, 0xFFFF00, 0x00FFFF, 0x00FF00,
                    0xFF00FF, 0xFF0000, 0x0000FF, 0x000000,
                )
                val rgb = IntArray(sw * sh)
                for (y in 0 until sh) {
                    for (x in 0 until sw) {
                        rgb[y * sw + x] = if (y < sh / 2) {
                            bars[(x * bars.size) / sw]
                        } else {
                            val g = x * 255 / (sw - 1)
                            (g shl 16) or (g shl 8) or g
                        }
                    }
                }
                val packed = HtcYuv.rgbToPackedRgba(rgb, sw, sh)
                val synthetic = listOf(
                    Triple("synthetic_bypass", HtcEffect.NONE, VintageVariant.CROSS),
                    Triple("synthetic_cross", HtcEffect.VINTAGE, VintageVariant.CROSS),
                    Triple("synthetic_sepia", HtcEffect.VINTAGE, VintageVariant.SEPIA),
                    Triple("synthetic_contrast", HtcEffect.CONTRAST, VintageVariant.CROSS),
                    Triple("synthetic_bloom", HtcEffect.BLOOM, VintageVariant.CROSS),
                    Triple("synthetic_mirror", HtcEffect.MIRROR, VintageVariant.CROSS),
                    Triple("synthetic_vignette", HtcEffect.VIGNETTE, VintageVariant.CROSS),
                )
                val report = StringBuilder()
                for ((name, effect, variant) in synthetic) {
                    val jpeg = processor.processPacked(packed.copyOf(), sw, sh, effect, variant)
                    File(dir, "$name.jpg").writeBytes(jpeg)
                    report.append("$name: ${jpeg.size / 1024} KiB\n")
                }
                setStatus("synthetic done\n$report")
            } catch (t: Throwable) {
                Log.e(TAG, "synthetic failed", t)
                setStatus("synthetic failed: ${t.message}")
            } finally {
                processor.close()
            }
        }
    }

    private fun process(image: Image) {
        val width = image.width
        val height = image.height
        setStatus("processing ${width}x$height ...")
        worker.execute {
            val start = System.currentTimeMillis()
            val dir = File(getExternalFilesDir(null), "selftest").apply { mkdirs() }
            val processor = HtcProcessor(this)
            try {
                val cases = listOf(
                    Triple("bypass", HtcEffect.NONE, VintageVariant.CROSS),
                    Triple("vintage_cross", HtcEffect.VINTAGE, VintageVariant.CROSS),
                    Triple("vintage_sepia", HtcEffect.VINTAGE, VintageVariant.SEPIA),
                )
                val report = StringBuilder()
                for ((name, effect, variant) in cases) {
                    val t0 = System.currentTimeMillis()
                    val jpeg = processor.process(image, effect, variant, large = false)
                    File(dir, "$name.jpg").writeBytes(jpeg)
                    val dt = System.currentTimeMillis() - t0
                    report.append("$name: ${jpeg.size / 1024} KiB, ${dt} ms\n")
                }
                image.close()
                val total = System.currentTimeMillis() - start
                setStatus("done in $total ms\n${dir.absolutePath}\n$report")
            } catch (t: Throwable) {
                Log.e(TAG, "self test failed", t)
                image.close()
                setStatus("failed: ${t.message}")
            } finally {
                processor.close()
                closeCamera()
            }
        }
    }

    private fun closeCamera() {
        try {
            session?.close()
        } catch (_: Exception) {
        }
        try {
            camera?.close()
        } catch (_: Exception) {
        }
        try {
            reader?.close()
        } catch (_: Exception) {
        }
        thread?.quitSafely()
    }

    override fun onDestroy() {
        super.onDestroy()
        closeCamera()
        worker.shutdown()
    }

    private companion object {
        const val TAG = "HtcSelfTest"
    }
}
