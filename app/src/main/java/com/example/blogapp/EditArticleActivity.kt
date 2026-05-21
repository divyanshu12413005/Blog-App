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
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore

class EditArticleActivity : AppCompatActivity() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var realtimeDb: FirebaseDatabase

    private lateinit var progressBar: ProgressBar
    private lateinit var btnSave: MaterialButton
    private lateinit var etTitle: TextInputEditText
    private lateinit var etContent: TextInputEditText
    private lateinit var etCategory: TextInputEditText
    private lateinit var ivCover: ImageView

    private var blogId: String? = null
    private var coverImageUri: Uri? = null
    private var currentImageUrl: String? = null

    private val pickImage = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            coverImageUri = it
            Glide.with(this)
                .load(it)
                .centerCrop()
                .into(ivCover)
            ivCover.alpha = 1f
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_article)

        firestore = FirebaseFirestore.getInstance()
        realtimeDb = FirebaseDatabase.getInstance()

        progressBar = findViewById(R.id.editProgressBar)
        btnSave = findViewById(R.id.btnSaveEdit)
        etTitle = findViewById(R.id.etEditBlogTitle)
        etContent = findViewById(R.id.etEditBlogContent)
        etCategory = findViewById(R.id.etEditCategory)
        ivCover = findViewById(R.id.editBlogCoverImage)

        blogId = intent.getStringExtra("blogId")
        val title = intent.getStringExtra("title")
        val description = intent.getStringExtra("description")
        val category = intent.getStringExtra("category")
        currentImageUrl = intent.getStringExtra("imageUrl")

        etTitle.setText(title)
        etContent.setText(description)
        etCategory.setText(category)

        if (!currentImageUrl.isNullOrEmpty()) {
            Glide.with(this)
                .load(currentImageUrl)
                .centerCrop()
                .into(ivCover)
            ivCover.alpha = 1f
        }

        findViewById<ImageView>(R.id.btnBackEdit).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        ivCover.setOnClickListener {
            pickImage.launch("image/*")
        }

        btnSave.setOnClickListener {
            updateBlog()
        }
    }

    private fun updateBlog() {
        val title = etTitle.text.toString().trim()
        val content = etContent.text.toString().trim()
        val category = etCategory.text.toString().trim()

        if (title.isEmpty()) {
            Toast.makeText(this, "Title is required", Toast.LENGTH_SHORT).show()
            return
        }

        if (content.isEmpty()) {
            Toast.makeText(this, "Content is required", Toast.LENGTH_SHORT).show()
            return
        }

        val id = blogId
        if (id.isNullOrEmpty()) {
            Toast.makeText(this, "Blog ID missing", Toast.LENGTH_SHORT).show()
            return
        }

        showLoading(true)

        if (coverImageUri != null) {
            uploadImageAndUpdate(id, title, content, category.ifEmpty { "General" })
        } else {
            updateBothDatabases(
                blogId = id,
                title = title,
                content = content,
                category = category.ifEmpty { "General" },
                imageUrl = currentImageUrl
            )
        }
    }

    private fun uploadImageAndUpdate(
        blogId: String,
        title: String,
        content: String,
        category: String
    ) {
        MediaManager.get()
            .upload(coverImageUri)
            .option("upload_preset", "blog_app_preset")
            .option("folder", "blog_covers")
            .callback(object : UploadCallback {
                override fun onSuccess(requestId: String, resultData: Map<*, *>) {
                    val imageUrl = resultData["secure_url"].toString()
                    updateBothDatabases(blogId, title, content, category, imageUrl)
                }

                override fun onError(requestId: String?, error: ErrorInfo) {
                    Toast.makeText(
                        this@EditArticleActivity,
                        "Image upload failed, saving without new image",
                        Toast.LENGTH_SHORT
                    ).show()
                    updateBothDatabases(blogId, title, content, category, currentImageUrl)
                }

                override fun onStart(requestId: String) {}
                override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {}
                override fun onReschedule(requestId: String, error: ErrorInfo) {}
            })
            .dispatch()
    }

    private fun updateBothDatabases(
        blogId: String,
        title: String,
        content: String,
        category: String,
        imageUrl: String?
    ) {
        val updates = hashMapOf<String, Any?>(
            "title" to title,
            "description" to content,
            "category" to category,
            "imageUrl" to imageUrl
        )

        firestore.collection("blogs")
            .document(blogId)
            .update(updates as Map<String, Any>)
            .addOnSuccessListener {
                updateRealtimeDatabase(blogId, title, content, category, imageUrl)
            }
            .addOnFailureListener {
                showLoading(false)
                Toast.makeText(this, "Firestore update failed", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateRealtimeDatabase(
        blogId: String,
        title: String,
        content: String,
        category: String,
        imageUrl: String?
    ) {
        val updates = hashMapOf<String, Any?>(
            "title" to title,
            "description" to content,
            "category" to category,
            "imageUrl" to imageUrl
        )

        realtimeDb.reference.child("blogs")
            .child(blogId)
            .updateChildren(updates as Map<String, Any>)
            .addOnSuccessListener {
                // Also update in current user's saved_blogs if it exists
                val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                if (uid != null) {
                    firestore.collection("users").document(uid).collection("saved_blogs")
                        .document(blogId).get().addOnSuccessListener { doc ->
                            if (doc.exists()) {
                                firestore.collection("users").document(uid).collection("saved_blogs")
                                    .document(blogId).update(updates as Map<String, Any>)
                            }
                        }
                }
                
                showLoading(false)
                showAlert("Success", "Blog updated successfully")
            }
            .addOnFailureListener {
                showLoading(false)
                Toast.makeText(this, "Realtime Database update failed", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        btnSave.isEnabled = !show
    }

    private fun showAlert(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .show()
    }
}