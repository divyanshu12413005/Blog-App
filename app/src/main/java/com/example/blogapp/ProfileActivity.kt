package com.example.blogapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
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
import com.example.blogapp.register.WelcomeActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore

class ProfileActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var database: FirebaseDatabase
    private lateinit var progressBar: ProgressBar
    private var currentImageUrl: String? = null

    private val imagePickerLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                uploadProfileImage(uri)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        database = FirebaseDatabase.getInstance()

        val backBtn = findViewById<ImageView>(R.id.btnBackProfile)
        val profileImage = findViewById<ImageView>(R.id.profileImageProfile)
        val cameraIcon = findViewById<ImageView>(R.id.cameraIconProfile)
        val nameText = findViewById<TextView>(R.id.profileNameText)
        val emailText = findViewById<TextView>(R.id.profileEmailText)
        val addArticleBtn = findViewById<Button>(R.id.btnAddArticle)
        val yourArticlesBtn = findViewById<Button>(R.id.btnYourArticles)
        val logoutBtn = findViewById<Button>(R.id.btnLogout)
        val editNameBtn = findViewById<ImageView>(R.id.btnEditName)
        progressBar = findViewById(R.id.profileProgressBar)

        backBtn.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        profileImage.setOnClickListener {
            if (!currentImageUrl.isNullOrEmpty()) {
                startActivity(
                    Intent(this, ImagePreviewActivity::class.java).apply {
                        putExtra("imageUrl", currentImageUrl)
                    }
                )
            }
        }

        cameraIcon.setOnClickListener {
            showImageOptionsDialog()
        }

        editNameBtn.setOnClickListener {
            showEditNameDialog(nameText.text.toString())
        }

        addArticleBtn.setOnClickListener {
            startActivity(Intent(this, AddBlogActivity::class.java))
        }

        yourArticlesBtn.setOnClickListener {
            startActivity(Intent(this, MyArticlesActivity::class.java))
        }

        logoutBtn.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, WelcomeActivity::class.java))
            finish()
        }

        loadProfile(profileImage, nameText, emailText)
    }

    private fun showEditNameDialog(currentName: String) {
        val editText = EditText(this)
        editText.setText(currentName)
        editText.setSelection(currentName.length)
        editText.setTextColor(resources.getColor(R.color.black, theme))
        editText.setHintTextColor(resources.getColor(R.color.black, theme))
        
        val padding = (24 * resources.displayMetrics.density).toInt()
        val container = android.widget.FrameLayout(this)
        val params = android.widget.FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.leftMargin = padding
        params.rightMargin = padding
        params.topMargin = padding / 2
        editText.layoutParams = params
        container.addView(editText)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Edit Name")
            .setView(container)
            .setPositiveButton("UPDATE", null)
            .setNegativeButton("CANCEL") { d, _ -> d.dismiss() }
            .create()

        dialog.setOnShowListener {
            val updateBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            updateBtn.setTextColor(resources.getColor(R.color.blue, theme))
            updateBtn.setOnClickListener {
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty() && newName != currentName) {
                    updateName(newName)
                    dialog.dismiss()
                } else if (newName.isEmpty()) {
                    editText.error = "Name cannot be empty"
                } else {
                    dialog.dismiss()
                }
            }
            
            val cancelBtn = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            cancelBtn.setTextColor(resources.getColor(R.color.black, theme))
        }
        
        dialog.show()
    }

    private fun updateName(newName: String) {
        val uid = auth.currentUser?.uid ?: return
        showLoading(true)

        // 1. Update User Profile in Firestore
        firestore.collection("users").document(uid)
            .update("name", newName)
            .addOnSuccessListener {
                // 2. Update User Profile in Realtime Database
                database.reference.child("users").child(uid).child("name").setValue(newName)

                // 3. Update Name in all Blogs authored by this user
                firestore.collection("blogs")
                    .whereEqualTo("authorId", uid)
                    .get()
                    .addOnSuccessListener { documents ->
                        val batch = firestore.batch()
                        for (document in documents) {
                            batch.update(document.reference, "author", newName)
                            // Also update Realtime Database for each blog
                            database.reference.child("blogs").child(document.id).child("author").setValue(newName)
                        }
                        
                        // 4. Update Name in Saved Blogs (if they exist as copies)
                        firestore.collection("users").document(uid).collection("saved_blogs")
                            .get()
                            .addOnSuccessListener { savedDocs ->
                                for (doc in savedDocs) {
                                    if (doc.getString("authorId") == uid) {
                                        batch.update(doc.reference, "author", newName)
                                    }
                                }
                                
                                batch.commit().addOnSuccessListener {
                                    findViewById<TextView>(R.id.profileNameText).text = newName
                                    showLoading(false)
                                    Toast.makeText(this, "Name updated everywhere!", Toast.LENGTH_SHORT).show()
                                }
                                .addOnFailureListener { e ->
                                    showLoading(false)
                                    Toast.makeText(this, "Batch commit failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .addOnFailureListener {
                                // If saved_blogs fails, still try to commit the blogs update
                                batch.commit().addOnSuccessListener {
                                    findViewById<TextView>(R.id.profileNameText).text = newName
                                    showLoading(false)
                                    Toast.makeText(this, "Name updated in blogs!", Toast.LENGTH_SHORT).show()
                                }
                            }
                    }
                    .addOnFailureListener { e ->
                        showLoading(false)
                        findViewById<TextView>(R.id.profileNameText).text = newName
                        Toast.makeText(this, "User name updated, but failed to update blogs: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(this, "Failed to update name: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showImageOptionsDialog() {
        val options = arrayOf("Upload New Photo", "Remove Current Photo", "Cancel")
        AlertDialog.Builder(this)
            .setTitle("Profile Photo")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> imagePickerLauncher.launch("image/*")
                    1 -> {
                        if (!currentImageUrl.isNullOrEmpty()) {
                            removeProfileImage()
                        } else {
                            Toast.makeText(this, "No photo to remove", Toast.LENGTH_SHORT).show()
                        }
                    }
                    2 -> dialog.dismiss()
                }
            }
            .show()
    }

    private fun removeProfileImage() {
        val uid = auth.currentUser?.uid ?: return
        showLoading(true)

        firestore.collection("users").document(uid)
            .update("profileImageUrl", null)
            .addOnSuccessListener {
                currentImageUrl = null
                database.reference.child("users").child(uid).child("profileImageUrl").removeValue()
                
                firestore.collection("blogs")
                    .whereEqualTo("authorId", uid)
                    .get()
                    .addOnSuccessListener { documents ->
                        val batch = firestore.batch()
                        for (document in documents) {
                            batch.update(document.reference, "profileImageUrl", null)
                            database.reference.child("blogs").child(document.id).child("profileImageUrl").removeValue()
                        }
                        
                        firestore.collection("users").document(uid).collection("saved_blogs")
                            .get()
                            .addOnSuccessListener { savedDocs ->
                                for (doc in savedDocs) {
                                    if (doc.getString("authorId") == uid) {
                                        batch.update(doc.reference, "profileImageUrl", null)
                                    }
                                }
                                
                                batch.commit().addOnSuccessListener {
                                    showLoading(false)
                                    Toast.makeText(this, "Profile picture removed!", Toast.LENGTH_SHORT).show()
                                    findViewById<ImageView>(R.id.profileImageProfile).setImageResource(R.drawable.ic_person)
                                }
                            }
                    }
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(this, "Failed to remove photo: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun uploadProfileImage(uri: Uri) {
        val uid = auth.currentUser?.uid ?: return
        showLoading(true)
        
        MediaManager.get()
            .upload(uri)
            .option("upload_preset", "blog_app_preset")
            .option("folder", "profile_photos")
            .option("public_id", "profile_${uid}_${System.currentTimeMillis()}")
            .callback(object : UploadCallback {
                override fun onSuccess(requestId: String, resultData: Map<*, *>) {
                    val imageUrl = resultData["secure_url"].toString()
                    updateProfileImageUrl(imageUrl)
                }
                override fun onError(requestId: String?, error: ErrorInfo) {
                    showLoading(false)
                    Toast.makeText(this@ProfileActivity, "Upload failed: ${error.description}", Toast.LENGTH_SHORT).show()
                }
                override fun onStart(requestId: String) {}
                override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {}
                override fun onReschedule(requestId: String, error: ErrorInfo) {}
            })
            .dispatch()
    }

    private fun updateProfileImageUrl(imageUrl: String) {
        val uid = auth.currentUser?.uid ?: return

        firestore.collection("users").document(uid)
            .update("profileImageUrl", imageUrl)
            .addOnSuccessListener {
                currentImageUrl = imageUrl
                database.reference.child("users").child(uid).child("profileImageUrl").setValue(imageUrl)
                
                firestore.collection("blogs")
                    .whereEqualTo("authorId", uid)
                    .get()
                    .addOnSuccessListener { documents ->
                        val batch = firestore.batch()
                        for (document in documents) {
                            batch.update(document.reference, "profileImageUrl", imageUrl)
                            database.reference.child("blogs").child(document.id).child("profileImageUrl").setValue(imageUrl)
                        }
                        
                        firestore.collection("users").document(uid).collection("saved_blogs")
                            .get()
                            .addOnSuccessListener { savedDocs ->
                                for (doc in savedDocs) {
                                    if (doc.getString("authorId") == uid) {
                                        batch.update(doc.reference, "profileImageUrl", imageUrl)
                                    }
                                }
                                
                                batch.commit().addOnSuccessListener {
                                    showLoading(false)
                                    Toast.makeText(this, "Profile picture updated everywhere!", Toast.LENGTH_SHORT).show()
                                }
                            }
                    }

                Glide.with(this)
                    .load(imageUrl)
                    .placeholder(R.drawable.ic_person)
                    .into(findViewById<ImageView>(R.id.profileImageProfile))
            }
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        findViewById<ImageView>(R.id.profileImageProfile).alpha = if (show) 0.5f else 1.0f
        findViewById<ImageView>(R.id.cameraIconProfile).isEnabled = !show
        findViewById<ImageView>(R.id.btnEditName).isEnabled = !show
    }

    private fun loadProfile(
        imageView: ImageView,
        nameText: TextView,
        emailText: TextView
    ) {
        val uid = auth.currentUser?.uid ?: return
        firestore.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val name = doc.getString("name") ?: "User"
                val email = doc.getString("email") ?: (auth.currentUser?.email ?: "")
                nameText.text = name
                emailText.text = email
                currentImageUrl = doc.getString("profileImageUrl")

                if (!currentImageUrl.isNullOrEmpty()) {
                    Glide.with(this)
                        .load(currentImageUrl)
                        .placeholder(R.drawable.ic_person)
                        .into(imageView)
                } else {
                    imageView.setImageResource(R.drawable.ic_person)
                }
            }
    }
}