package com.example.studybuddy

import android.os.Bundle
import android.widget.ImageButton
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide

class ImageDetailActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_detail)

        val imageUrl = intent.getStringExtra("IMAGE_URL")
        val ivDetailImage = findViewById<ImageView>(R.id.ivDetailImage)
        val btnBack = findViewById<ImageButton>(R.id.btnBack)

        Glide.with(this).load(imageUrl).into(ivDetailImage)

        btnBack.setOnClickListener {
            supportFinishAfterTransition()
        }
    }
}
