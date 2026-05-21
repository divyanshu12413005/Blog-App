package com.example.blogapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore

class AddBlogActivity : AppCompatActivity() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var progressBar: ProgressBar
    private lateinit var btnPublish: MaterialButton

    private var coverImageUri: Uri? = null

    private val pickImage = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            coverImageUri = it
            val imageView = findViewById<ImageView>(R.id.blogCoverImage)
            Glide.with(this)
                .load(it)
                .centerCrop()
                .into(imageView)
            imageView.alpha = 1f
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_blog)

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()

        progressBar = findViewById(R.id.progressBar)
        btnPublish = findViewById(R.id.btnPublish)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        findViewById<ImageView>(R.id.blogCoverImage).setOnClickListener {
            pickImage.launch("image/*")
        }

        btnPublish.setOnClickListener {
            publishBlog()
        }
    }

    private fun publishBlog() {
        val title = findViewById<TextInputEditText>(R.id.etBlogTitle)
            .text.toString().trim()

        val content = findViewById<TextInputEditText>(R.id.etBlogContent)
            .text.toString().trim()

        val category = findViewById<TextInputEditText>(R.id.etCategory)
            .text.toString().trim()

        if (title.isEmpty()) {
            showAlert("Missing Title", "Please enter a blog title!")
            return
        }

        if (content.isEmpty()) {
            showAlert("Missing Content", "Please write some blog content!")
            return
        }

        showLoading(true)

        if (coverImageUri != null) {
            uploadImageAndPublish(title, content, category.ifEmpty { "General" })
        } else {
            saveBlogToFirestore(title, content, category.ifEmpty { "General" }, null)
        }
    }

    private fun uploadImageAndPublish(title: String, content: String, category: String) {
        val userId = auth.currentUser?.uid ?: return

        MediaManager.get()
            .upload(coverImageUri)
            .option("upload_preset", "blog_app_preset")
            .option("folder", "blog_covers")
            .option("public_id", "blog_${userId}_${System.currentTimeMillis()}")
            .callback(object : UploadCallback {
                override fun onSuccess(requestId: String, resultData: Map<*, *>) {
                    val imageUrl = resultData["secure_url"].toString()
                    saveBlogToFirestore(title, content, category, imageUrl)
                }

                override fun onError(requestId: String?, error: ErrorInfo) {
                    showAlert(
                        "Upload Failed",
                        "Image upload failed!\nPublishing without cover image."
                    )
                    saveBlogToFirestore(title, content, category, null)
                }

                override fun onStart(requestId: String) {}
                override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {}
                override fun onReschedule(requestId: String, error: ErrorInfo) {}
            })
            .dispatch()
    }

    private fun saveBlogToFirestore(
        title: String,
        content: String,
        category: String,
        imageUrl: String?
    ) {
        val userId = auth.currentUser?.uid ?: return

        firestore.collection("users")
            .document(userId)
            .get()
            .addOnSuccessListener { document ->

                val authorName = document.getString("name") ?: "Anonymous"
                val authorImageUrl = document.getString("profileImageUrl")

                val blogId = firestore.collection("blogs").document().id

                val blog = hashMapOf(
                    "blogId" to blogId,
                    "title" to title,
                    "description" to content,
                    "author" to authorName,
                    "authorId" to userId,
                    "profileImageUrl" to authorImageUrl,
                    "imageUrl" to imageUrl,
                    "category" to category,
                    "date" to getCurrentDate(),
                    "createdAt" to System.currentTimeMillis(),
                    "likes" to 0,
                    "saves" to 0
                )

                firestore.collection("blogs")
                    .document(blogId)
                    .set(blog)
                    .addOnSuccessListener {

                        database.reference
                            .child("blogs")
                            .child(blogId)
                            .setValue(blog)
                            .addOnSuccessListener {
                                showLoading(false)
                                showAlert(
                                    "Published! 🎉",
                                    "Your blog has been published successfully!"
                                )
                                clearFields()
                            }
                            .addOnFailureListener { e ->
                                showLoading(false)
                                showAlert(
                                    "Published!",
                                    "Firestore saved but RTDB failed: ${e.message}"
                                )
                                clearFields()
                            }
                    }
                    .addOnFailureListener { e ->
                        showLoading(false)
                        showAlert("Failed", "Failed to publish: ${e.message}")
                    }
            }
            .addOnFailureListener { e ->
                showLoading(false)
                showAlert("Failed", "Failed to load user info: ${e.message}")
            }
    }

    private fun clearFields() {
        findViewById<TextInputEditText>(R.id.etBlogTitle).text?.clear()
        findViewById<TextInputEditText>(R.id.etBlogContent).text?.clear()
        findViewById<TextInputEditText>(R.id.etCategory).text?.clear()

        findViewById<ImageView>(R.id.blogCoverImage).setImageResource(R.drawable.pagetexture)
        findViewById<ImageView>(R.id.blogCoverImage).alpha = 0.4f

        coverImageUri = null
    }

    private fun getCurrentDate(): String {
        val sdf = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
        return sdf.format(java.util.Date())
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        btnPublish.isEnabled = !show
        btnPublish.alpha = if (show) 0.6f else 1.0f
    }

    private fun showAlert(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
                if (title.contains("Published", ignoreCase = true)) {
                    val intent = Intent(this, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
            }
            .show()
    }
}