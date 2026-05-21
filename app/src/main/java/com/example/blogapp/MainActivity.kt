package com.example.blogapp

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.blogapp.adapter.BlogAdapter
import com.example.blogapp.data.Blog
import com.example.blogapp.register.WelcomeActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var blogAdapter: BlogAdapter
    private val blogList = mutableListOf<Blog>()
    private val fullBlogList = mutableListOf<Blog>() // Keep the full list for search master copy

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        val profileImage = findViewById<ImageView>(R.id.profileImageMain)
        val bookmarkIcon = findViewById<ImageView>(R.id.bookmarkIcon)
        val feedsRecyclerView = findViewById<RecyclerView>(R.id.feedsRecyclerView)
        val fabWriteBlog = findViewById<FloatingActionButton>(R.id.fabWriteBlog)
        val searchEditText = findViewById<EditText>(R.id.searchEditText)

        fabWriteBlog.setOnClickListener {
            startActivity(Intent(this, AddBlogActivity::class.java))
        }

        bookmarkIcon.setOnClickListener {
            startActivity(Intent(this, SavedArticlesActivity::class.java))
        }

        profileImage.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        feedsRecyclerView.layoutManager = LinearLayoutManager(this)
        blogAdapter = BlogAdapter(blogList)
        feedsRecyclerView.adapter = blogAdapter

        // Implement Search Logic
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterBlogs(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        loadUserProfileImage(profileImage)
        fetchBlogs(searchEditText)
    }

    private fun fetchBlogs(searchEditText: EditText) {
        firestore.collection("blogs")
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Toast.makeText(this, "Failed to fetch blogs", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                fullBlogList.clear()
                if (snapshots != null) {
                    for (document in snapshots) {
                        val blog = document.toObject(Blog::class.java)
                        blog.blogId = document.id
                        fullBlogList.add(blog)
                    }
                }

                // Sort master list locally by newest first
                fullBlogList.sortByDescending { it.createdAt }

                // Apply current filter if user is already searching
                filterBlogs(searchEditText.text.toString())
            }
    }

    private fun filterBlogs(query: String) {
        blogList.clear()
        if (query.isEmpty()) {
            blogList.addAll(fullBlogList)
        } else {
            val lowerQuery = query.lowercase().trim()
            for (blog in fullBlogList) {
                if (blog.title?.lowercase()?.contains(lowerQuery) == true ||
                    blog.description?.lowercase()?.contains(lowerQuery) == true ||
                    blog.category?.lowercase()?.contains(lowerQuery) == true ||
                    blog.author?.lowercase()?.contains(lowerQuery) == true
                ) {
                    blogList.add(blog)
                }
            }
            if (blogList.isEmpty()) {
                Toast.makeText(this, "No post available for \"$query\"", Toast.LENGTH_SHORT).show()
            }
        }
        blogAdapter.notifyDataSetChanged()
    }

    private fun loadUserProfileImage(imageView: ImageView) {
        val userId = auth.currentUser?.uid

        if (userId != null) {
            firestore.collection("users").document(userId)
                .addSnapshotListener { document, e ->
                    if (e != null || document == null || !document.exists()) {
                        return@addSnapshotListener
                    }
                    
                    val profileImageUrl = document.getString("profileImageUrl")

                    if (!profileImageUrl.isNullOrEmpty()) {
                        Glide.with(this)
                            .load(profileImageUrl)
                            .placeholder(R.drawable.ic_person)
                            .circleCrop()
                            .into(imageView)
                    }
                }
        }
    }
}