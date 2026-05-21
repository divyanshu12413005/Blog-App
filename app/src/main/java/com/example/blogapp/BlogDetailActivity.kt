package com.example.blogapp

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.bumptech.glide.Glide
import com.example.blogapp.data.Blog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class BlogDetailActivity : AppCompatActivity() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private var isSaved = false
    private var isLiked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_blog_detail)

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()

        // Toolbar back button
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = ""

        // Get data from intent
        val title = intent.getStringExtra("title") ?: ""
        val description = intent.getStringExtra("description") ?: ""
        val author = intent.getStringExtra("author") ?: ""
        val date = intent.getStringExtra("date") ?: ""
        val imageUrl = intent.getStringExtra("imageUrl")
        val authorImageUrl = intent.getStringExtra("authorImageUrl")
        val category = intent.getStringExtra("category") ?: "General"
        val blogId = intent.getStringExtra("blogId") ?: ""
        val authorId = intent.getStringExtra("authorId")

        // Set data
        findViewById<TextView>(R.id.detailTitle).text = title
        findViewById<TextView>(R.id.detailContent).text = description
        findViewById<TextView>(R.id.detailAuthorName).text = author
        findViewById<TextView>(R.id.detailDate).text = date
        findViewById<TextView>(R.id.detailCategory).text = category

        // Load images
        val blogImage = findViewById<ImageView>(R.id.detailBlogImage)
        val authorImage = findViewById<ImageView>(R.id.detailAuthorImage)
        val btnSave = findViewById<ImageView>(R.id.btnSave)
        val btnLike = findViewById<ImageView>(R.id.btnLike)
        val txtLikeCount = findViewById<TextView>(R.id.detailLikeCount)
        val txtSaveCount = findViewById<TextView>(R.id.detailSaveCount)

        Glide.with(this)
            .load(imageUrl)
            .placeholder(R.drawable.pagetexture)
            .into(blogImage)

        // Real-time Author Profile Image
        if (!authorId.isNullOrEmpty()) {
            firestore.collection("users").document(authorId)
                .addSnapshotListener { document, _ ->
                    val freshImageUrl = document?.getString("profileImageUrl")
                    Glide.with(this)
                        .load(freshImageUrl ?: R.drawable.ic_person)
                        .placeholder(R.drawable.ic_person)
                        .circleCrop()
                        .into(authorImage)
                }
        } else {
            Glide.with(this)
                .load(authorImageUrl)
                .placeholder(R.drawable.ic_person)
                .circleCrop()
                .into(authorImage)
        }

        // Real-time Likes and Saves counts
        if (blogId.isNotEmpty()) {
            firestore.collection("blogs").document(blogId)
                .addSnapshotListener { doc, _ ->
                    if (doc != null && doc.exists()) {
                        val freshLikes = doc.getLong("likes")?.toInt() ?: 0
                        val freshSaves = doc.getLong("saves")?.toInt() ?: 0
                        txtLikeCount.text = freshLikes.toString()
                        txtSaveCount.text = freshSaves.toString()
                    }
                }
        }

        // Check User-specific Like and Save status
        val uid = auth.currentUser?.uid
        if (uid != null && blogId.isNotEmpty()) {
            firestore.collection("blogs").document(blogId).collection("likes").document(uid).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        isLiked = true
                        btnLike.setImageResource(R.drawable.redhearticon)
                    }
                }

            firestore.collection("users").document(uid).collection("saved_blogs").document(blogId).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        isSaved = true
                        btnSave.setImageResource(R.drawable.saveredwhite)
                    }
                }
        }

        // Save button
        btnSave.setOnClickListener {
            toggleSave(uid, blogId, title, description, author, date, imageUrl, authorImageUrl, category, authorId)
        }

        // Like button
        btnLike.setOnClickListener {
            toggleLike(uid, blogId)
        }

        // Author Profile Click
        val onAuthorClick = android.view.View.OnClickListener {
            if (!authorId.isNullOrEmpty()) {
                val intent = android.content.Intent(this, AuthorProfileActivity::class.java).apply {
                    putExtra("authorId", authorId)
                    putExtra("authorName", author)
                }
                startActivity(intent)
            }
        }
        authorImage.setOnClickListener(onAuthorClick)
        findViewById<TextView>(R.id.detailAuthorName).setOnClickListener(onAuthorClick)

        // Share button
        findViewById<ImageView>(R.id.btnShare).setOnClickListener {
            shareBlog(title, description)
        }
    }

    private fun shareBlog(title: String, description: String) {
        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND)
        shareIntent.type = "text/plain"
        val shareBody = "Check out this blog: $title\n\n$description"
        shareIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, title)
        shareIntent.putExtra(android.content.Intent.EXTRA_TEXT, shareBody)
        startActivity(android.content.Intent.createChooser(shareIntent, "Share Blog via"))
    }

    private fun toggleLike(uid: String?, blogId: String) {
        if (uid == null) {
            Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show()
            return
        }

        val btnLike = findViewById<ImageView>(R.id.btnLike)
        val blogRef = firestore.collection("blogs").document(blogId)
        val likeRef = blogRef.collection("likes").document(uid)
        val rtdbRef = database.reference.child("blogs").child(blogId)
        
        if (isLiked) {
            likeRef.delete().addOnSuccessListener {
                isLiked = false
                btnLike.setImageResource(R.drawable.whitehearticon)
                blogRef.update("likes", FieldValue.increment(-1))
                rtdbRef.child("likes").setValue(com.google.firebase.database.ServerValue.increment(-1))
                rtdbRef.child("likedBy").child(uid).removeValue()
                Toast.makeText(this, "Unliked", Toast.LENGTH_SHORT).show()
            }
        } else {
            likeRef.set(mapOf("liked" to true)).addOnSuccessListener {
                isLiked = true
                btnLike.setImageResource(R.drawable.redhearticon)
                blogRef.update("likes", FieldValue.increment(1))
                rtdbRef.child("likes").setValue(com.google.firebase.database.ServerValue.increment(1))
                rtdbRef.child("likedBy").child(uid).setValue(true)
                Toast.makeText(this, "Liked! ❤️", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun toggleSave(
        uid: String?, blogId: String, title: String, description: String, 
        author: String, date: String, imageUrl: String?, authorImageUrl: String?,
        category: String, authorId: String?
    ) {
        if (uid == null) {
            Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show()
            return
        }

        val btnSave = findViewById<ImageView>(R.id.btnSave)
        val blogRef = firestore.collection("blogs").document(blogId)
        val saveRef = firestore.collection("users").document(uid).collection("saved_blogs").document(blogId)
        val rtdbRef = database.reference.child("blogs").child(blogId)

        if (isSaved) {
            saveRef.delete().addOnSuccessListener {
                isSaved = false
                btnSave.setImageResource(R.drawable.savewhite)
                blogRef.update("saves", FieldValue.increment(-1))
                rtdbRef.child("saves").setValue(com.google.firebase.database.ServerValue.increment(-1))
                rtdbRef.child("savedBy").child(uid).removeValue()
                Toast.makeText(this, "Unsaved", Toast.LENGTH_SHORT).show()
            }
        } else {
            val savedBlog = hashMapOf(
                "title" to title,
                "description" to description,
                "author" to author,
                "authorId" to (authorId ?: ""),
                "date" to date,
                "imageUrl" to imageUrl,
                "profileImageUrl" to authorImageUrl,
                "category" to category,
                "savedAt" to System.currentTimeMillis()
            )
            saveRef.set(savedBlog).addOnSuccessListener {
                isSaved = true
                btnSave.setImageResource(R.drawable.saveredwhite)
                blogRef.update("saves", FieldValue.increment(1))
                rtdbRef.child("saves").setValue(com.google.firebase.database.ServerValue.increment(1))
                rtdbRef.child("savedBy").child(uid).setValue(true)
                Toast.makeText(this, "Blog saved! ✅", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}