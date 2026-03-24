package com.kitoptics.thermalview.app

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import com.kitoptics.thermalview.databinding.ActivityMainBinding
import com.kitoptics.thermalview.pipeline.FrameDistributor
import com.kitoptics.thermalview.pipeline.TripleBuffer
import com.kitoptics.thermalview.rendering.ThermalSurfaceView
import com.kitoptics.thermalview.ui.CameraViewModel
import com.kitoptics.thermalview.usb.ACTION_USB_PERMISSION
import com.kitoptics.thermalview.usb.AusbcBridge
import com.kitoptics.thermalview.usb.CAMERA_HEIGHT
import com.kitoptics.thermalview.usb.CAMERA_WIDTH
import com.kitoptics.thermalview.usb.KITEYE_PID
import com.kitoptics.thermalview.usb.KITEYE_VID
import com.kitoptics.thermalview.usb.UsbCameraState
import com.kitoptics.thermalview.usb.UvcStreamController
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: CameraViewModel by viewModels()

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) checkAlreadyConnectedDevice()
    }

    private lateinit var tripleBuffer: TripleBuffer
    private lateinit var distributor: FrameDistributor
    private lateinit var thermalView: ThermalSurfaceView

    private var usbManager: UsbManager? = null
    private var usbReceiver: BroadcastReceiver? = null
    private var pendingDevice: UsbDevice? = null
    private var ausbcBridge: AusbcBridge? = null
    private lateinit var streamController: UvcStreamController

    // FPS update: every 500ms, not every frame
    private val fpsHandler = Handler(Looper.getMainLooper())
    private val fpsRunnable = object : Runnable {
        override fun run() {
            viewModel.onFpsUpdate(
                fps = streamController.getFps(),
                bandwidthMbps = streamController.getBandwidthMbps(),
                avgIntervalMs = streamController.getAvgIntervalMs(),
            )
            fpsHandler.postDelayed(this, 500)
        }
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        usbManager = getSystemService(Context.USB_SERVICE) as UsbManager

        // Pre-allocate frame pipeline
        tripleBuffer = TripleBuffer(CAMERA_WIDTH * CAMERA_HEIGHT)
        distributor = FrameDistributor(tripleBuffer)
        streamController = UvcStreamController(distributor)

        // Setup GL preview
        thermalView = ThermalSurfaceView(this)
        thermalView.init(distributor)
        binding.previewContainer.addView(thermalView)

        setupFabs()
        observeState()
        registerUsbReceiver()

        // Handle auto-launch from USB_DEVICE_ATTACHED intent
        handleIntent(intent)

        // POST_NOTIFICATIONS: request on API 33+ so AUSBC internal notifications don't throw
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}.launch(
                Manifest.permission.POST_NOTIFICATIONS
            )
        }

        // Ensure CAMERA permission (required by Samsung for UVC devices)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            checkAlreadyConnectedDevice()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun checkAlreadyConnectedDevice() {
        usbManager?.deviceList?.values?.forEach { device ->
            if (device.vendorId == KITEYE_VID && device.productId == KITEYE_PID) {
                requestPermission(device)
                return
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            }
            device?.let { requestPermission(it) }
        }
    }

    private fun requestPermission(device: UsbDevice) {
        if (device.vendorId != KITEYE_VID || device.productId != KITEYE_PID) return
        val hasPerm = usbManager?.hasPermission(device) == true
        android.util.Log.d("KitEye", "requestPermission: VID=${device.vendorId} PID=${device.productId} hasPerm=$hasPerm")
        pendingDevice = device
        viewModel.onDeviceDetected()
        if (hasPerm) {
            viewModel.onPermissionGranted()
        } else {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT else 0
            val permIntent = PendingIntent.getBroadcast(
                this, device.productId,   // unique request code per device
                Intent(ACTION_USB_PERMISSION).setPackage(packageName),
                flags
            )
            pendingDevice = device
            android.util.Log.d("KitEye", "calling usbManager.requestPermission for ${device.deviceName}")
            usbManager?.requestPermission(device, permIntent)
        }
    }

    private fun openCamera() {
        val device = pendingDevice ?: run {
            android.util.Log.e("KitEye", "openCamera: no pending device")
            return
        }
        ausbcBridge?.close()
        streamController.onStreamStarted()
        ausbcBridge = AusbcBridge(
            context = this,
            onFrame = { data -> streamController.onFrameReceived(data) },
            onOpened = { format -> viewModel.onStreamingStarted(streamController.getFps(), format) },
            onError = { msg -> viewModel.onError(msg) }
        )
        ausbcBridge?.open(device)
    }

    private fun closeCamera() {
        ausbcBridge?.close()
        ausbcBridge = null
        streamController.stopStream()
    }

    private fun setupFabs() {
        binding.fabPalette.setOnClickListener {
            viewModel.togglePalette()
            thermalView.setBlackHot(viewModel.isBlackHot)
            binding.fabPalette.contentDescription =
                if (viewModel.isBlackHot) "Black-hot active" else "White-hot active"
        }

        binding.fabRecord.setOnClickListener {
            // Phase 3: recording — placeholder
            Snackbar.make(binding.root, "Recording: Phase 3", Snackbar.LENGTH_SHORT).show()
        }

        // One-shot calibration: send NUC/FFC trigger, then disable until next connection
        binding.fabCalibrate.setOnClickListener {
            ausbcBridge?.calibrate()
            binding.fabCalibrate.isEnabled = false
            Snackbar.make(binding.root, "Calibration sent", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.cameraState.collect { state ->
                    updateUi(state)
                }
            }
        }
    }

    private fun updateUi(state: UsbCameraState) {
        when (state) {
            is UsbCameraState.Disconnected -> {
                binding.tvStatus.text = "Connect KitEye camera via USB-C"
                binding.tvStatus.visibility = View.VISIBLE
                binding.statsBar.text = "FPS: --  USB: disconnected"
                binding.fabRecord.isEnabled = false
                binding.fabPalette.isEnabled = false
                binding.fabCalibrate.isEnabled = false
                fpsHandler.removeCallbacks(fpsRunnable)
                closeCamera()
            }
            is UsbCameraState.RequestingPermission -> {
                binding.tvStatus.text = "Requesting USB permission..."
                binding.tvStatus.visibility = View.VISIBLE
            }
            is UsbCameraState.Connecting -> {
                binding.tvStatus.text = "Connecting to camera..."
                binding.tvStatus.visibility = View.VISIBLE
                openCamera()
            }
            is UsbCameraState.Streaming -> {
                binding.tvStatus.visibility = View.GONE
                binding.statsBar.text = "FPS:${"%.1f".format(state.fps)}" +
                    "  BW:${"%.1f".format(state.bandwidthMbps)}MB/s" +
                    "  Lat:${state.avgIntervalMs.toInt()}ms" +
                    "  ${state.format.label}"
                binding.fabRecord.isEnabled = true
                binding.fabPalette.isEnabled = true
                binding.fabCalibrate.isEnabled = true   // reset one-shot on each new connection
                fpsHandler.post(fpsRunnable)
            }
            is UsbCameraState.Recording -> {
                binding.tvStatus.visibility = View.GONE
                binding.statsBar.text = "● REC  FPS: ${"%.1f".format(state.fps)}"
                binding.fabRecord.isEnabled = true
                binding.fabPalette.isEnabled = true
            }
            is UsbCameraState.Error -> {
                binding.tvStatus.text = "Error: ${state.message}"
                binding.tvStatus.visibility = View.VISIBLE
                Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                binding.fabRecord.isEnabled = false
                binding.fabPalette.isEnabled = false
                binding.fabCalibrate.isEnabled = false
            }
        }
    }

    private fun registerUsbReceiver() {
        usbReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                } ?: return

                when (intent.action) {
                    UsbManager.ACTION_USB_DEVICE_ATTACHED -> requestPermission(device)
                    UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                        if (device.vendorId == KITEYE_VID && device.productId == KITEYE_PID) {
                            viewModel.onCameraDisconnected()
                        }
                    }
                    ACTION_USB_PERMISSION -> {
                        val intentGranted = intent.getBooleanExtra(
                            UsbManager.EXTRA_PERMISSION_GRANTED, false
                        )
                        val nowHasPerm = usbManager?.hasPermission(device) == true
                        android.util.Log.d("KitEye", "USB_PERMISSION broadcast: intentGranted=$intentGranted nowHasPerm=$nowHasPerm device=${device.deviceName}")
                        val actualGranted = intentGranted || nowHasPerm
                        if (actualGranted) viewModel.onPermissionGranted()
                        else viewModel.onPermissionDenied()
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(ACTION_USB_PERMISSION)
        }

        registerReceiver(usbReceiver, filter, RECEIVER_NOT_EXPORTED)
    }

    override fun onResume() {
        super.onResume()
        thermalView.onResume()
    }

    override fun onPause() {
        super.onPause()
        thermalView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        fpsHandler.removeCallbacks(fpsRunnable)
        closeCamera()
        usbReceiver?.let { unregisterReceiver(it) }
        usbReceiver = null
    }
}
