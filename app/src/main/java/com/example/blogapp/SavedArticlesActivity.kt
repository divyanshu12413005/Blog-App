package com.example.blogapp

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.blogapp.adapter.BlogAdapter
import com.example.blogapp.data.Blog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class SavedArticlesActivity : AppCompatActivity() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyText: TextView
    private lateinit var adapter: BlogAdapter

    private val savedBlogs = mutableListOf<Blog>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_saved_articles)

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        recyclerView = findViewById(R.id.savedRecyclerView)
        emptyText = findViewById(R.id.emptySavedText)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = BlogAdapter(savedBlogs)
        recyclerView.adapter = adapter

        findViewById<ImageView>(R.id.btnBackSaved).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        loadSavedBlogs()
    }

    private fun loadSavedBlogs() {
        val uid = auth.currentUser?.uid ?: return

        firestore.collection("users")
            .document(uid)
            .collection("saved_blogs")
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    emptyText.visibility = View.VISIBLE
                    emptyText.text = "Failed to load saved articles"
                    return@addSnapshotListener
                }

                savedBlogs.clear()
                if (snapshots != null) {
                    for (document in snapshots) {
                        val blog = document.toObject(Blog::class.java)
                        blog.blogId = document.id
                        savedBlogs.add(blog)
                    }
                }

                // Sort locally by savedAt descending
                savedBlogs.sortByDescending { blog ->
                    // Since saved_blogs might not have 'createdAt' in some versions,
                    // we use 'savedAt' or fallback to 0
                    try {
                        val savedAtField = snapshots?.documents?.find { it.id == blog.blogId }?.get("savedAt") as? Long
                        savedAtField ?: 0L
                    } catch (e: Exception) {
                        0L
                    }
                }

                adapter.notifyDataSetChanged()
                emptyText.visibility = if (savedBlogs.isEmpty()) View.VISIBLE else View.GONE
            }
    }
}