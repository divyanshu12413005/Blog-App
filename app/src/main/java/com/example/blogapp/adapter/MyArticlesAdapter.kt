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
import com.example.blogapp.EditArticleActivity
import com.example.blogapp.R
import com.example.blogapp.data.Blog
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.FirebaseFirestore

class MyArticlesAdapter(
    private val blogs: MutableList<Blog>
) : RecyclerView.Adapter<MyArticlesAdapter.MyBlogViewHolder>() {

    private val firestore = FirebaseFirestore.getInstance()

    class MyBlogViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.myBlogTitle)
        val description: TextView = view.findViewById(R.id.myBlogDescription)
        val authorName: TextView = view.findViewById(R.id.myAuthorName)
        val date: TextView = view.findViewById(R.id.myPostDate)
        val blogImage: ImageView = view.findViewById(R.id.myBlogImage)
        val authorImage: ImageView = view.findViewById(R.id.myAuthorImage)
        val category: TextView = view.findViewById(R.id.myBlogCategory)

        val btnReadMore: MaterialButton = view.findViewById(R.id.btnMyReadMore)
        val btnEdit: MaterialButton = view.findViewById(R.id.btnMyEdit)
        val btnDelete: MaterialButton = view.findViewById(R.id.btnMyDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyBlogViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_my_article, parent, false)
        return MyBlogViewHolder(view)
    }

    override fun onBindViewHolder(holder: MyBlogViewHolder, position: Int) {
        val blog = blogs[position]
        val context = holder.itemView.context

        holder.title.text = blog.title ?: ""
        holder.description.text = blog.description ?: ""
        holder.authorName.text = blog.author ?: "Anonymous"
        holder.date.text = blog.date ?: ""
        holder.category.text = blog.category ?: "General"

        if (!blog.imageUrl.isNullOrEmpty()) {
            Glide.with(context)
                .load(blog.imageUrl)
                .placeholder(R.drawable.pagetexture)
                .centerCrop()
                .into(holder.blogImage)
        } else {
            holder.blogImage.setImageResource(R.drawable.pagetexture)
        }

        if (!blog.profileImageUrl.isNullOrEmpty()) {
            Glide.with(context)
                .load(blog.profileImageUrl)
                .placeholder(R.drawable.ic_person)
                .circleCrop()
                .into(holder.authorImage)
        } else {
            holder.authorImage.setImageResource(R.drawable.ic_person)
        }

        holder.btnReadMore.setOnClickListener {
            val intent = Intent(context, BlogDetailActivity::class.java).apply {
                putExtra("title", blog.title ?: "")
                putExtra("description", blog.description ?: "")
                putExtra("author", blog.author ?: "Anonymous")
                putExtra("date", blog.date ?: "")
                putExtra("imageUrl", blog.imageUrl)
                putExtra("authorImageUrl", blog.profileImageUrl)
                putExtra("category", blog.category ?: "General")
                putExtra("blogId", blog.blogId ?: "")
                putExtra("authorId", blog.authorId ?: "")
            }
            context.startActivity(intent)
        }

        holder.btnEdit.setOnClickListener {
            val intent = Intent(context, EditArticleActivity::class.java).apply {
                putExtra("blogId", blog.blogId)
                putExtra("title", blog.title ?: "")
                putExtra("description", blog.description ?: "")
                putExtra("category", blog.category ?: "General")
                putExtra("imageUrl", blog.imageUrl)
            }
            context.startActivity(intent)
        }

        holder.btnDelete.setOnClickListener {
            val blogId = blog.blogId
            if (blogId.isNullOrEmpty()) {
                Toast.makeText(context, "Blog ID not found", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            firestore.collection("blogs")
                .document(blogId)
                .delete()
                .addOnSuccessListener {
                    val currentPos = holder.adapterPosition
                    if (currentPos != RecyclerView.NO_POSITION) {
                        blogs.removeAt(currentPos)
                        notifyItemRemoved(currentPos)
                        notifyItemRangeChanged(currentPos, blogs.size)
                    }
                    Toast.makeText(context, "Blog deleted", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener {
                    Toast.makeText(context, "Delete failed", Toast.LENGTH_SHORT).show()
                }
        }
    }

    override fun getItemCount(): Int = blogs.size
}