package com.example.blogapp.data

data class Blog(
    var blogId: String? = null,
    val title: String? = null,
    val description: String? = null,
    val author: String? = null,
    val authorId: String? = null,
    val date: String? = null,
    val imageUrl: String? = null,
    val profileImageUrl: String? = null,
    val category: String? = "General",
    val createdAt: Long? = null,
    val likes: Int = 0,
    val saves: Int = 0
)