package com.example.blogapp

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.blogapp.adapter.MyArticlesAdapter
import com.example.blogapp.data.Blog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class MyArticlesActivity : AppCompatActivity() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyText: TextView
    private lateinit var adapter: MyArticlesAdapter

    private val myBlogs = mutableListOf<Blog>()
    private var blogsListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_articles)

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        recyclerView = findViewById(R.id.myArticlesRecyclerView)
        emptyText = findViewById(R.id.emptyMyArticlesText)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = MyArticlesAdapter(myBlogs)
        recyclerView.adapter = adapter

        findViewById<ImageView>(R.id.btnBackMyArticles).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        loadMyBlogs()
    }

    override fun onResume() {
        super.onResume()
        loadMyBlogs()
    }

    override fun onDestroy() {
        super.onDestroy()
        blogsListener?.remove()
    }

    private fun loadMyBlogs() {
        val uid = auth.currentUser?.uid ?: return

        blogsListener?.remove()

        blogsListener = firestore.collection("blogs")
            .whereEqualTo("authorId", uid)
            .addSnapshotListener { documents, e ->
                if (e != null) {
                    emptyText.visibility = View.VISIBLE
                    emptyText.text = "Failed to load your articles"
                    return@addSnapshotListener
                }

                myBlogs.clear()

                if (documents != null) {
                    for (document in documents) {
                        val blog = document.toObject(Blog::class.java)
                        blog.blogId = document.id
                        myBlogs.add(blog)
                    }
                }

                adapter.notifyDataSetChanged()
                emptyText.visibility = if (myBlogs.isEmpty()) View.VISIBLE else View.GONE
            }
    }
}