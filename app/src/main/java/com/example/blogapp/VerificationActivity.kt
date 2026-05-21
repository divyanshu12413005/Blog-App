package com.example.blogapp

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class VerificationActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_verification)

        findViewById<android.widget.Button>(R.id.verifyButton).setOnClickListener {
            val code = findViewById<android.widget.EditText>(R.id.codeEditText).text.toString()
            if (code.length == 4) {
                val intent = android.content.Intent(this, ResetPasswordActivity::class.java)
                startActivity(intent)
                finish()
            } else {
                android.widget.Toast.makeText(this, "Please enter 4 digit code", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
}