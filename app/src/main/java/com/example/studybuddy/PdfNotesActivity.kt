package com.example.studybuddy

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage

class PdfNotesActivity : AppCompatActivity() {

    private lateinit var database: DatabaseReference
    private lateinit var storage: FirebaseStorage
    private lateinit var adapter: PdfAdapter
    private val pdfList = mutableListOf<PdfFile>()
    private lateinit var progressBar: ProgressBar

    private val pdfPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { uploadPdf(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pdf_notes)

        database = FirebaseDatabase.getInstance().getReference("pdfs")
        storage = FirebaseStorage.getInstance()
        
        progressBar = findViewById(R.id.progressBar)
        val rvPdfs = findViewById<RecyclerView>(R.id.rvPdfs)
        val fabAdd = findViewById<ExtendedFloatingActionButton>(R.id.fabAddPdf)
        val btnBack = findViewById<ImageButton>(R.id.btnBack)

        btnBack.setOnClickListener { finish() }
        fabAdd.setOnClickListener { pdfPicker.launch("application/pdf") }

        adapter = PdfAdapter(pdfList, { pdf ->
            // View PDF logic
            viewPdf(pdf)
        }, { pdf ->
            // Download PDF logic
            downloadPdf(pdf)
        })

        rvPdfs.layoutManager = LinearLayoutManager(this)
        rvPdfs.adapter = adapter

        loadPdfs()
    }

    private fun loadPdfs() {
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                pdfList.clear()
                for (data in snapshot.children) {
                    val pdf = data.getValue(PdfFile::class.java)?.copy(id = data.key)
                    if (pdf != null) pdfList.add(pdf)
                }
                adapter.notifyDataSetChanged()
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun uploadPdf(uri: Uri) {
        progressBar.visibility = View.VISIBLE
        val fileName = getFileName(uri) ?: "Note_${System.currentTimeMillis()}.pdf"
        val ref = storage.getReference("pdfs/${System.currentTimeMillis()}_$fileName")

        ref.putFile(uri).addOnSuccessListener {
            ref.downloadUrl.addOnSuccessListener { downloadUri ->
                val pdfFile = PdfFile(
                    fileName = fileName,
                    downloadUrl = downloadUri.toString(),
                    uploaderId = FirebaseAuth.getInstance().currentUser?.uid
                )
                database.push().setValue(pdfFile).addOnCompleteListener {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this, "Upload Successful", Toast.LENGTH_SHORT).show()
                }
            }
        }.addOnFailureListener {
            progressBar.visibility = View.GONE
            Toast.makeText(this, "Upload Failed: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun viewPdf(pdf: PdfFile) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(pdf.downloadUrl), "application/pdf")
            flags = Intent.FLAG_ACTIVITY_NO_HISTORY
        }
        val chooser = Intent.createChooser(intent, "Open PDF with")
        try {
            startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(this, "No PDF viewer found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun downloadPdf(pdf: PdfFile) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(pdf.downloadUrl))
        startActivity(intent)
        Toast.makeText(this, "Starting download...", Toast.LENGTH_SHORT).show()
    }

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index != -1) result = cursor.getString(index)
                }
            } finally {
                cursor?.close()
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) result = result?.substring(cut + 1)
        }
        return result
    }
}
