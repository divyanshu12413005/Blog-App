package com.example.blogapp.register

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.blogapp.R
import com.example.blogapp.SignAndRegisterActivity

class WelcomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcome)

        findViewById<Button>(R.id.loginButton).setOnClickListener {
            val intent = Intent(this, SignAndRegisterActivity::class.java)
            intent.putExtra("action", "login")
            startActivity(intent)
        }

        findViewById<Button>(R.id.registerButton).setOnClickListener {
            val intent = Intent(this, SignAndRegisterActivity::class.java)
            intent.putExtra("action", "register")
            startActivity(intent)
        }
    }
}