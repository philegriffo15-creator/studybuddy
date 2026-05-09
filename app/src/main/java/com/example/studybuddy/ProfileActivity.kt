package com.example.studybuddy

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage

class ProfileActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference
    private lateinit var storage: FirebaseStorage
    
    private lateinit var ivProfileImage: ImageView
    private lateinit var tvName: TextView
    private lateinit var tvEmail: TextView
    private lateinit var tvCourse: TextView
    private lateinit var tvBio: TextView
    private lateinit var tvStreak: TextView
    private lateinit var tvStudyHours: TextView
    private lateinit var tvGroups: TextView
    private lateinit var tvResidence: TextView

    private var profileListener: ValueEventListener? = null
    private val PICK_IMAGE_REQUEST = 71
    private var imageUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        auth = FirebaseAuth.getInstance()
        storage = FirebaseStorage.getInstance()
        val currentUser = auth.currentUser
        
        ivProfileImage = findViewById(R.id.ivProfileImage)
        tvName = findViewById(R.id.tvProfileName)
        tvEmail = findViewById(R.id.tvUserEmail)
        tvCourse = findViewById(R.id.tvCourseDetail)
        tvBio = findViewById(R.id.tvBio)
        tvStreak = findViewById(R.id.tvStreakCount)
        tvStudyHours = findViewById(R.id.tvStudyHours)
        tvGroups = findViewById(R.id.tvGroupsCount)
        tvResidence = findViewById(R.id.tvResidence)
        
        val btnBack = findViewById<ImageView>(R.id.btnBack)
        val btnEditProfile = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnEditProfile)
        
        val statStudyHours = findViewById<LinearLayout>(R.id.statStudyHours)
        val statGroups = findViewById<LinearLayout>(R.id.statGroups)
        val statStreak = findViewById<LinearLayout>(R.id.statStreak)

        btnBack.setOnClickListener { finish() }

        if (currentUser != null) {
            tvEmail.text = currentUser.email
            database = FirebaseDatabase.getInstance().getReference("Users").child(currentUser.uid)
            
            // Load User Data
            profileListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val user = snapshot.getValue(User::class.java)
                    if (user != null) {
                        tvName.text = user.fullName
                        tvCourse.text = user.course ?: "No course set"
                        tvBio.text = user.bio ?: "Hey there! I am using StudyBuddy."
                        tvStreak.text = user.streak.toString()
                        tvStudyHours.text = user.studyHours.toString()
                        tvGroups.text = user.groupsJoined.toString()
                        tvResidence.text = user.residence ?: "Not set"
                        
                        if (!user.profileImageUrl.isNullOrEmpty()) {
                            Glide.with(this@ProfileActivity)
                                .load(user.profileImageUrl)
                                .placeholder(R.drawable.ic_launcher_foreground)
                                .into(ivProfileImage)
                        }
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            }
            database.addValueEventListener(profileListener!!)
        }

        ivProfileImage.setOnClickListener { chooseImage() }
        
        btnEditProfile.setOnClickListener {
            showEditProfileDialog()
        }

        statStudyHours.setOnClickListener { Toast.makeText(this, "Keep studying to increase your hours!", Toast.LENGTH_SHORT).show() }
        statGroups.setOnClickListener { Toast.makeText(this, "Join more groups in the Study Hub!", Toast.LENGTH_SHORT).show() }
        statStreak.setOnClickListener { Toast.makeText(this, "Daily activity maintains your streak!", Toast.LENGTH_SHORT).show() }
    }

    override fun onDestroy() {
        super.onDestroy()
        profileListener?.let { database.removeEventListener(it) }
    }

    private fun chooseImage() {
        val intent = Intent()
        intent.type = "image/*"
        intent.action = Intent.ACTION_GET_CONTENT
        startActivityForResult(Intent.createChooser(intent, "Select Picture"), PICK_IMAGE_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null && data.data != null) {
            imageUri = data.data
            Glide.with(this).load(imageUri).into(ivProfileImage)
            uploadImage()
        }
    }

    private fun uploadImage() {
        if (imageUri != null) {
            val uid = auth.currentUser?.uid ?: return
            val ref = storage.reference.child("profile_images/$uid")
            
            Toast.makeText(this, "Uploading...", Toast.LENGTH_SHORT).show()
            
            ref.putFile(imageUri!!).addOnSuccessListener {
                ref.downloadUrl.addOnSuccessListener { uri ->
                    database.child("profileImageUrl").setValue(uri.toString())
                    Toast.makeText(this, "Profile Updated", Toast.LENGTH_SHORT).show()
                }
            }.addOnFailureListener {
                Toast.makeText(this, "Upload failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showEditProfileDialog() {
        // Multi-field dialog or sequence of dialogs for Bio, Course, Residence
        val builder = AlertDialog.Builder(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_profile_multi, null)
        val etBio = view.findViewById<EditText>(R.id.etEditBio)
        val etCourse = view.findViewById<EditText>(R.id.etEditCourse)
        val etResidence = view.findViewById<EditText>(R.id.etEditResidence)
        
        etBio.setText(tvBio.text)
        etCourse.setText(tvCourse.text)
        etResidence.setText(tvResidence.text)

        builder.setView(view)
        builder.setTitle("Edit Profile")
        builder.setPositiveButton("Save") { _, _ ->
            val updates = mapOf(
                "bio" to etBio.text.toString().trim(),
                "course" to etCourse.text.toString().trim(),
                "residence" to etResidence.text.toString().trim()
            )
            database.updateChildren(updates).addOnSuccessListener {
                Toast.makeText(this, "Profile Updated", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }
}
