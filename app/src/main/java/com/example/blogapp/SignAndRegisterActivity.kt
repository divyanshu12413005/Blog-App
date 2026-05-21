package com.example.blogapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.example.blogapp.data.UserData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore

class SignAndRegisterActivity : AppCompatActivity() {

    private var isLoginMode = true
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var database: FirebaseDatabase
    private lateinit var progressBar: ProgressBar
    private lateinit var loginRegisterButton: Button
    private lateinit var profileImageContainer: FrameLayout
    private lateinit var uploadPhotoText: TextView
    private lateinit var profileImage: ImageView

    private var imageUri: Uri? = null

    private val imagePickerLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                imageUri = uri
                Glide.with(this).load(uri).into(profileImage)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_and_register)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        database = FirebaseDatabase.getInstance()

        progressBar = findViewById(R.id.progressBar)
        loginRegisterButton = findViewById(R.id.loginRegisterButton)
        profileImageContainer = findViewById(R.id.profileImageContainer)
        uploadPhotoText = findViewById(R.id.uploadPhotoText)
        profileImage = findViewById(R.id.profileImage)

        val toggleButton = findViewById<Button>(R.id.toggleButton)
        val forgotPasswordText = findViewById<TextView>(R.id.forgotPasswordText)

        profileImageContainer.setOnClickListener {
            if (!isLoginMode) {
                imagePickerLauncher.launch("image/*")
            }
        }

        forgotPasswordText.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }

        val action = intent.getStringExtra("action")
        if (action == "register") {
            setRegisterMode()
        } else {
            setLoginMode()
        }

        toggleButton.setOnClickListener {
            if (isLoginMode) setRegisterMode() else setLoginMode()
        }

        loginRegisterButton.setOnClickListener {
            handleAuth()
        }
    }

    private fun showLoading(show: Boolean) {
        if (show) {
            progressBar.visibility = View.VISIBLE
            loginRegisterButton.isEnabled = false
            loginRegisterButton.alpha = 0.6f
        } else {
            progressBar.visibility = View.GONE
            loginRegisterButton.isEnabled = true
            loginRegisterButton.alpha = 1.0f
        }
    }

    private fun showAlert(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun handleAuth() {
        val email = findViewById<EditText>(R.id.emailEditText).text.toString().trim()
        val password = findViewById<EditText>(R.id.passwordEditText).text.toString().trim()
        val name = findViewById<EditText>(R.id.nameEditText).text.toString().trim()

        if (isLoginMode) {
            if (email.isEmpty() || password.isEmpty()) {
                showAlert("Missing Fields", "Please fill all fields!")
                return
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                showAlert("Invalid Email", "Please enter a valid email address!")
                return
            }

            showLoading(true)

            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    showLoading(false)
                    if (task.isSuccessful) {
                        Toast.makeText(this, "Login successful", Toast.LENGTH_SHORT).show()
                        val intent = Intent(this, MainActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    } else {
                        val errorMessage = when {
                            task.exception?.message?.contains("no user record") == true ->
                                "You are not registered yet!\nPlease register first."

                            task.exception?.message?.contains("password is invalid") == true ||
                                    task.exception?.message?.contains("wrong-password") == true ||
                                    task.exception?.message?.contains("INVALID_LOGIN_CREDENTIALS") == true ->
                                "Wrong email or password!\nPlease try again."

                            task.exception?.message?.contains("badly formatted") == true ->
                                "Invalid email format!\nPlease check your email."

                            task.exception?.message?.contains("blocked") == true ||
                                    task.exception?.message?.contains("too-many-requests") == true ->
                                "Too many failed attempts!\nPlease try again later."

                            task.exception?.message?.contains("network") == true ->
                                "No internet connection!\nPlease check your network."

                            else ->
                                "Login failed!\n${task.exception?.message}"
                        }
                        showAlert("Login Failed", errorMessage)
                    }
                }
        } else {
            if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
                showAlert("Missing Fields", "Please fill all fields!")
                return
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                showAlert("Invalid Email", "Please enter a valid email address!")
                return
            }
            if (password.length < 6) {
                showAlert("Weak Password", "Password must be at least 6 characters!")
                return
            }

            showLoading(true)

            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val userId = auth.currentUser?.uid
                        if (userId != null) {
                            if (imageUri != null) {
                                uploadImageAndSaveUser(userId, name, email)
                            } else {
                                saveUserData(userId, name, email, null)
                            }
                        }
                    } else {
                        showLoading(false)
                        val errorMessage = when {
                            task.exception?.message?.contains(
                                "email address is already in use") == true ->
                                "This email is already registered!\nPlease login instead."

                            task.exception?.message?.contains("badly formatted") == true ->
                                "Invalid email format!\nPlease check your email."

                            task.exception?.message?.contains("network") == true ->
                                "No internet connection!\nPlease check your network."

                            else ->
                                "Registration failed!\n${task.exception?.message}"
                        }
                        showAlert("Registration Failed", errorMessage)
                    }
                }
        }
    }

    private fun uploadImageAndSaveUser(userId: String, name: String, email: String) {
        imageUri?.let { uri ->
            MediaManager.get()
                .upload(uri)
                .option("upload_preset", "blog_app_preset")
                .option("folder", "profile_photos")
                .option("public_id", userId)
                .callback(object : UploadCallback {

                    override fun onSuccess(
                        requestId: String,
                        resultData: Map<*, *>
                    ) {
                        val imageUrl = resultData["secure_url"].toString()
                        saveUserData(userId, name, email, imageUrl)
                    }

                    override fun onError(
                        requestId: String?,
                        error: ErrorInfo
                    ) {
                        showLoading(false)
                        showAlert(
                            "Upload Failed",
                            "Image upload failed!\nSaving profile without photo."
                        )
                        saveUserData(userId, name, email, null)
                    }

                    override fun onStart(requestId: String) {}
                    override fun onProgress(
                        requestId: String,
                        bytes: Long,
                        totalBytes: Long
                    ) {}
                    override fun onReschedule(
                        requestId: String,
                        error: ErrorInfo
                    ) {}
                })
                .dispatch()
        }
    }

    private fun saveUserData(
        userId: String,
        name: String,
        email: String,
        profileImageUrl: String?
    ) {
        val user = UserData(
            name = name,
            email = email,
            userId = userId,
            profileImageUrl = profileImageUrl
        )

        firestore.collection("users").document(userId)
            .set(user)
            .addOnSuccessListener {
                database.reference.child("users").child(userId)
                    .setValue(user)
                    .addOnSuccessListener {
                        showLoading(false)
                        showAlert(
                            "Success! 🎉",
                            "Registration successful!\nPlease login with your credentials."
                        )
                        auth.signOut()
                        setLoginMode()
                    }
                    .addOnFailureListener { e ->
                        showLoading(false)
                        showAlert("Database Error", "Realtime DB Error: ${e.message}")
                    }
            }
            .addOnFailureListener { e ->
                showLoading(false)
                showAlert("Database Error", "Firestore Error: ${e.message}")
            }
    }

    private fun setLoginMode() {
        isLoginMode = true
        profileImageContainer.visibility = View.GONE
        uploadPhotoText.visibility = View.GONE
        findViewById<EditText>(R.id.nameEditText).visibility = View.GONE
        findViewById<EditText>(R.id.emailEditText)
            .setBackgroundResource(R.drawable.edittext_border_red)
        findViewById<EditText>(R.id.passwordEditText)
            .setBackgroundResource(R.drawable.edittext_border_red)

        loginRegisterButton.text = getString(R.string.login)
        loginRegisterButton.setBackgroundResource(R.drawable.button_solid_red)

        val toggleButton = findViewById<Button>(R.id.toggleButton)
        toggleButton.text = getString(R.string.register)
        toggleButton.setBackgroundResource(R.drawable.button_border_blue)

        findViewById<TextView>(R.id.newHereText).text = getString(R.string.new_here)
        findViewById<TextView>(R.id.forgotPasswordText).visibility = View.VISIBLE
    }

    private fun setRegisterMode() {
        isLoginMode = false
        imageUri = null
        profileImage.setImageResource(R.drawable.ic_person)
        profileImageContainer.visibility = View.VISIBLE
        uploadPhotoText.visibility = View.VISIBLE
        findViewById<EditText>(R.id.nameEditText).visibility = View.VISIBLE
        findViewById<EditText>(R.id.emailEditText)
            .setBackgroundResource(R.drawable.edittext_border_blue)
        findViewById<EditText>(R.id.passwordEditText)
            .setBackgroundResource(R.drawable.edittext_border_blue)

        loginRegisterButton.text = getString(R.string.register)
        loginRegisterButton.setBackgroundResource(R.drawable.button_solid_blue)

        val toggleButton = findViewById<Button>(R.id.toggleButton)
        toggleButton.text = getString(R.string.login)
        toggleButton.setBackgroundResource(R.drawable.button_border_red)

        findViewById<TextView>(R.id.newHereText).text = "Already have an account?"
        findViewById<TextView>(R.id.forgotPasswordText).visibility = View.GONE
    }
}