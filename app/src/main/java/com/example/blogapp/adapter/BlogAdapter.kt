package com.example.blogapp.adapter

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.blogapp.BlogDetailActivity
import com.example.blogapp.R
import com.example.blogapp.data.Blog
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class BlogAdapter(private val blogs: MutableList<Blog>) :
    RecyclerView.Adapter<BlogAdapter.BlogViewHolder>() {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance()

    class BlogViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.blogTitle)
        val description: TextView = view.findViewById(R.id.blogDescription)
        val authorName: TextView = view.findViewById(R.id.authorName)
        val date: TextView = view.findViewById(R.id.postDate)
        val blogImage: ImageView = view.findViewById(R.id.blogImage)
        val authorImage: ImageView = view.findViewById(R.id.authorImage)
        val category: TextView = view.findViewById(R.id.blogCategory)
        val btnReadMore: MaterialButton = view.findViewById(R.id.btnReadMore)
        val btnLike: ImageView = view.findViewById(R.id.btnLike)
        val btnSave: ImageView = view.findViewById(R.id.btnSave)
        val txtLikeCount: TextView = view.findViewById(R.id.txtLikeCount)
        val txtSaveCount: TextView = view.findViewById(R.id.txtSaveCount)

        var likeListener: ListenerRegistration? = null
        var saveListener: ListenerRegistration? = null
        var authorProfileListener: ListenerRegistration? = null
        var blogDataListener: ListenerRegistration? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BlogViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_blog, parent, false)
        return BlogViewHolder(view)
    }

    override fun onBindViewHolder(holder: BlogViewHolder, position: Int) {
        val blog = blogs[position]
        val uid = auth.currentUser?.uid
        val blogId = blog.blogId ?: ""
        val authorId = blog.authorId

        holder.title.text = blog.title ?: ""
        holder.description.text = blog.description ?: ""
        holder.authorName.text = blog.author ?: "Anonymous"
        holder.date.text = blog.date ?: ""
        holder.category.text = blog.category ?: "General"
        holder.txtLikeCount.text = blog.likes.toString()
        holder.txtSaveCount.text = blog.saves.toString()

        // Cancel existing listeners
        holder.likeListener?.remove()
        holder.saveListener?.remove()
        holder.authorProfileListener?.remove()
        holder.blogDataListener?.remove()

        // Default icons
        holder.btnLike.setImageResource(R.drawable.whitehearticon)
        holder.btnSave.setImageResource(R.drawable.savewhite)

        // Real-time Blog Data (Likes/Saves Counts)
        if (blogId.isNotEmpty()) {
            holder.blogDataListener = firestore.collection("blogs").document(blogId)
                .addSnapshotListener { doc, _ ->
                    if (doc != null && doc.exists()) {
                        val freshLikes = doc.getLong("likes")?.toInt() ?: 0
                        val freshSaves = doc.getLong("saves")?.toInt() ?: 0
                        holder.txtLikeCount.text = freshLikes.toString()
                        holder.txtSaveCount.text = freshSaves.toString()
                    }
                }
        }

        // Real-time Author Profile Image
        if (!authorId.isNullOrEmpty()) {
            holder.authorProfileListener = firestore.collection("users").document(authorId)
                .addSnapshotListener { document, _ ->
                    val freshImageUrl = document?.getString("profileImageUrl")
                    Glide.with(holder.itemView.context)
                        .load(freshImageUrl ?: R.drawable.ic_person)
                        .placeholder(R.drawable.ic_person)
                        .circleCrop()
                        .into(holder.authorImage)
                }
        } else {
            holder.authorImage.setImageResource(R.drawable.ic_person)
        }

        if (uid != null && blogId.isNotEmpty()) {
            // Real-time User-specific Like Status
            holder.likeListener = firestore.collection("blogs").document(blogId)
                .collection("likes").document(uid)
                .addSnapshotListener { doc, _ ->
                    if (doc != null && doc.exists()) {
                        holder.btnLike.setImageResource(R.drawable.redhearticon)
                    } else {
                        holder.btnLike.setImageResource(R.drawable.whitehearticon)
                    }
                }

            // Real-time User-specific Save Status
            holder.saveListener = firestore.collection("users").document(uid)
                .collection("saved_blogs").document(blogId)
                .addSnapshotListener { doc, _ ->
                    if (doc != null && doc.exists()) {
                        holder.btnSave.setImageResource(R.drawable.saveredwhite)
                    } else {
                        holder.btnSave.setImageResource(R.drawable.savewhite)
                    }
                }
        }

        if (!blog.imageUrl.isNullOrEmpty()) {
            Glide.with(holder.itemView.context)
                .load(blog.imageUrl)
                .placeholder(R.drawable.pagetexture)
                .centerCrop()
                .into(holder.blogImage)
        } else {
            holder.blogImage.setImageResource(R.drawable.pagetexture)
        }

        holder.btnReadMore.setOnClickListener {
            val context = holder.itemView.context
            val intent = Intent(context, BlogDetailActivity::class.java).apply {
                putExtra("title", blog.title ?: "")
                putExtra("description", blog.description ?: "")
                putExtra("author", blog.author ?: "Anonymous")
                putExtra("date", blog.date ?: "")
                putExtra("imageUrl", blog.imageUrl)
                putExtra("authorImageUrl", blog.profileImageUrl)
                putExtra("category", blog.category ?: "General")
                putExtra("blogId", blogId)
                putExtra("authorId", authorId)
            }
            context.startActivity(intent)
        }

        // Click listener for Author Profile
        val onAuthorClick = View.OnClickListener {
            if (!authorId.isNullOrEmpty()) {
                val context = holder.itemView.context
                val intent = Intent(context, com.example.blogapp.AuthorProfileActivity::class.java).apply {
                    putExtra("authorId", authorId)
                    putExtra("authorName", blog.author ?: "Author")
                }
                context.startActivity(intent)
            }
        }

        holder.authorImage.setOnClickListener(onAuthorClick)
        holder.authorName.setOnClickListener(onAuthorClick)

        holder.btnLike.setOnClickListener {
            if (uid == null) {
                Toast.makeText(holder.itemView.context, "Login to like", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val blogRef = firestore.collection("blogs").document(blogId)
            val likeRef = blogRef.collection("likes").document(uid)
            val rtdbRef = database.reference.child("blogs").child(blogId)
            
            likeRef.get().addOnSuccessListener { doc ->
                if (doc.exists()) {
                    // Unlike
                    likeRef.delete().addOnSuccessListener {
                        blogRef.update("likes", FieldValue.increment(-1))
                        rtdbRef.child("likes").setValue(ServerValue.increment(-1))
                        rtdbRef.child("likedBy").child(uid).removeValue()
                    }
                } else {
                    // Like
                    likeRef.set(mapOf("liked" to true)).addOnSuccessListener {
                        blogRef.update("likes", FieldValue.increment(1))
                        rtdbRef.child("likes").setValue(ServerValue.increment(1))
                        rtdbRef.child("likedBy").child(uid).setValue(true)
                    }
                }
            }
        }

        holder.btnSave.setOnClickListener {
            if (uid == null) {
                Toast.makeText(holder.itemView.context, "Login to save", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val blogRef = firestore.collection("blogs").document(blogId)
            val saveRef = firestore.collection("users").document(uid).collection("saved_blogs").document(blogId)
            val rtdbRef = database.reference.child("blogs").child(blogId)
            
            saveRef.get().addOnSuccessListener { doc ->
                if (doc.exists()) {
                    // Unsave
                    saveRef.delete().addOnSuccessListener {
                        blogRef.update("saves", FieldValue.increment(-1))
                        rtdbRef.child("saves").setValue(ServerValue.increment(-1))
                        rtdbRef.child("savedBy").child(uid).removeValue()
                        Toast.makeText(holder.itemView.context, "Unsaved", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // Save
                    val savedData = hashMapOf(
                        "title" to (blog.title ?: ""),
                        "description" to (blog.description ?: ""),
                        "author" to (blog.author ?: "Anonymous"),
                        "authorId" to (blog.authorId ?: ""),
                        "date" to (blog.date ?: ""),
                        "imageUrl" to blog.imageUrl,
                        "profileImageUrl" to blog.profileImageUrl,
                        "category" to (blog.category ?: "General"),
                        "savedAt" to System.currentTimeMillis()
                    )
                    saveRef.set(savedData).addOnSuccessListener {
                        blogRef.update("saves", FieldValue.increment(1))
                        rtdbRef.child("saves").setValue(ServerValue.increment(1))
                        rtdbRef.child("savedBy").child(uid).setValue(true)
                        Toast.makeText(holder.itemView.context, "Saved", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onViewRecycled(holder: BlogViewHolder) {
        super.onViewRecycled(holder)
        holder.likeListener?.remove()
        holder.saveListener?.remove()
        holder.authorProfileListener?.remove()
        holder.blogDataListener?.remove()
        holder.likeListener = null
        holder.saveListener = null
        holder.authorProfileListener = null
        holder.blogDataListener = null
    }

    override fun getItemCount(): Int = blogs.size
}