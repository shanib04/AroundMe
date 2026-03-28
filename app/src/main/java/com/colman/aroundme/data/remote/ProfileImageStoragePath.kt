package com.colman.aroundme.data.remote

object ProfileImageStoragePath {
    fun forUser(userId: String): String = "profile_images/$userId/avatar.jpg"
}