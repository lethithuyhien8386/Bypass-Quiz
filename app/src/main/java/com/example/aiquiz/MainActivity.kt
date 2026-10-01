package com.example.aiquiz

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.content.Context
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.aiquiz.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.apiKeyInput.setText(prefs.getString("api_key", ""))
        refreshKeyStatus()
        binding.saveKeyButton.setOnClickListener {
            prefs.edit().putString("api_key", binding.apiKeyInput.text?.toString()?.trim().orEmpty()).apply()
            refreshKeyStatus(); Toast.makeText(this, "Đã lưu API key trên thiết bị", Toast.LENGTH_SHORT).show()
        }
        binding.accessibilityButton.setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        binding.overlayButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        binding.testScanButton.setOnClickListener { QuizAccessibilityService.instance?.scanAndAnswer() ?: Toast.makeText(this, "Hãy bật Accessibility trước", Toast.LENGTH_SHORT).show() }
        binding.autoButton.setOnClickListener {
            QuizAccessibilityService.autoMode = !QuizAccessibilityService.autoMode
            binding.autoButton.text = if (QuizAccessibilityService.autoMode) "■ Tắt tự động quét" else "▶ Bật tự động quét"
            binding.statusText.text = if (QuizAccessibilityService.autoMode) "Trạng thái: Tự động bật" else "Trạng thái: Tự động tắt"
        }
        QuizAccessibilityService.statusListener = { msg -> runOnUiThread { binding.statusText.text = "Trạng thái: $msg" } }
    }
    override fun onResume() { super.onResume(); refreshKeyStatus() }
    private fun refreshKeyStatus() { binding.keyStatus.text = if (prefs.getString("api_key", "").isNullOrBlank()) "Chưa lưu" else "✓ Đã lưu" }
}
