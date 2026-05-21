package com.example.blogapp

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.blogapp.adapter.BlogAdapter
import com.example.blogapp.data.Blog
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class AuthorProfileActivity : AppCompatActivity() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var blogAdapter: BlogAdapter
    private val blogList = mutableListOf<Blog>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_author_profile)

        firestore = FirebaseFirestore.getInstance()

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = ""

        val authorId = intent.getStringExtra("authorId") ?: ""
        val authorName = intent.getStringExtra("authorName") ?: "Author"
        
        val authorImage = findViewById<ImageView>(R.id.authorProfileImage)
        val authorNameText = findViewById<TextView>(R.id.authorProfileName)
        val authorEmailText = findViewById<TextView>(R.id.authorProfileEmail)
        val postCountText = findViewById<TextView>(R.id.postCountText)
        val recyclerView = findViewById<RecyclerView>(R.id.authorRecyclerView)

        authorNameText.text = authorName
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        blogAdapter = BlogAdapter(blogList)
        recyclerView.adapter = blogAdapter

        if (authorId.isNotEmpty()) {
            fetchAuthorDetails(authorId, authorImage, authorEmailText)
            fetchAuthorBlogs(authorId, postCountText)
        } else {
            Toast.makeText(this, "Author not found", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun fetchAuthorDetails(authorId: String, imageView: ImageView, emailText: TextView) {
        firestore.collection("users").document(authorId)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val email = document.getString("email")
                    val profileImageUrl = document.getString("profileImageUrl")

                    emailText.text = email ?: ""
                    
                    Glide.with(this)
                        .load(profileImageUrl ?: R.drawable.ic_person)
                        .placeholder(R.drawable.ic_person)
                        .circleCrop()
                        .into(imageView)
                }
            }
    }

    private fun fetchAuthorBlogs(authorId: String, countText: TextView) {
        firestore.collection("blogs")
            .whereEqualTo("authorId", authorId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    return@addSnapshotListener
                }

                blogList.clear()
                if (snapshots != null) {
                    for (document in snapshots) {
                        val blog = document.toObject(Blog::class.java)
                        blog.blogId = document.id
                        blogList.add(blog)
                    }
                    countText.text = "${blogList.size} Posts"
                    blogAdapter.notifyDataSetChanged()
                }
            }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}