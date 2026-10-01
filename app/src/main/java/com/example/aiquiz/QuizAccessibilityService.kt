package com.example.aiquiz

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.Display
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class QuizAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val busy = AtomicBoolean(false)
    private val handler = Handler(Looper.getMainLooper())
    private var pendingScan = false

    companion object {
        var instance: QuizAccessibilityService? = null
        var autoMode = false
        var statusListener: ((String) -> Unit)? = null
    }

    override fun onServiceConnected() { super.onServiceConnected(); instance = this; statusListener?.invoke("Accessibility: đã kết nối") }
    override fun onDestroy() { instance = null; super.onDestroy() }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!autoMode || event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (pendingScan) return
        pendingScan = true
        handler.postDelayed({
            pendingScan = false
            if (autoMode) scanAndAnswer()
        }, 1200)
    }
    override fun onInterrupt() {}

    fun scanAndAnswer() {
        if (!busy.compareAndSet(false, true)) return
        val key = getSharedPreferences("settings", MODE_PRIVATE).getString("api_key", "").orEmpty()
        if (key.isBlank()) { statusListener?.invoke("Chưa có API key"); busy.set(false); return }
        if (android.os.Build.VERSION.SDK_INT < 30) { statusListener?.invoke("Cần Android 11+"); busy.set(false); return }
        statusListener?.invoke("Đang chụp màn hình...")
        takeScreenshot(Display.DEFAULT_DISPLAY, PixelFormat.RGBA_8888, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                val hw = result.hardwareBuffer ?: run { busy.set(false); return }
                val cs = result.colorSpace ?: ColorSpace.get(ColorSpace.Named.SRGB)
                val bitmap = Bitmap.wrapHardwareBuffer(hw, cs)
                hw.close()
                if (bitmap == null) { statusListener?.invoke("Không đọc được ảnh màn hình"); busy.set(false); return }
                val copy = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                bitmap.recycle()
                scope.launch {
                    try {
                        statusListener?.invoke("Gemini đang phân tích...")
                        val answer = GeminiClient.solve(key, copy)
                        copy.recycle()
                        if (answer <= 0) statusListener?.invoke("Không tìm thấy đáp án rõ ràng")
                        else {
                            statusListener?.invoke("Gemini chọn đáp án #$answer")
                            tapChoice(answer)
                        }
                    } catch (e: Exception) { statusListener?.invoke("Lỗi: ${e.message?.take(180)}") }
                    finally { busy.set(false) }
                }
            }
            override fun onFailure(errorCode: Int) { statusListener?.invoke("Chụp màn hình thất bại: $errorCode"); busy.set(false) }
        })
    }

    private fun tapChoice(index: Int) {
        val root = rootInActiveWindow ?: run { statusListener?.invoke("Không đọc được giao diện hiện tại"); return }
        val candidates = ArrayList<Rect>()
        fun walk(node: android.view.accessibility.AccessibilityNodeInfo?) {
            if (node == null) return
            val text = node.text?.toString()?.trim().orEmpty()
            val desc = node.contentDescription?.toString()?.trim().orEmpty()
            val clickable = node.isClickable || node.isCheckable || node.isFocusable
            if (clickable && (text.isNotBlank() || desc.isNotBlank())) {
                val r = Rect(); node.getBoundsInScreen(r); if (r.width() > 80 && r.height() > 30) candidates.add(r)
            }
            for (i in 0 until node.childCount) walk(node.getChild(i))
        }
        walk(root)
        val unique = candidates.distinctBy { "${it.left},${it.top},${it.right},${it.bottom}" }
            .sortedWith(compareBy<Rect> { it.top }.thenBy { it.left })
        if (index > unique.size) { statusListener?.invoke("Không xác định được vị trí đáp án #$index"); return }
        val r = unique[index - 1]
        val path = Path().apply { moveTo(r.centerX().toFloat(), r.centerY().toFloat()) }
        val gesture = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 80)).build()
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) { statusListener?.invoke("Đã chạm đáp án #$index") }
            override fun onCancelled(gestureDescription: GestureDescription?) { statusListener?.invoke("Chạm đáp án bị hủy") }
        }, Handler(Looper.getMainLooper()))
    }
}
