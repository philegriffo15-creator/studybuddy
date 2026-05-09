package com.example.studybuddy

import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class PdfAdapter(
    private val pdfList: List<PdfFile>,
    private val onItemClick: (PdfFile) -> Unit,
    private val onDownloadClick: (PdfFile) -> Unit
) : RecyclerView.Adapter<PdfAdapter.PdfViewHolder>() {

    class PdfViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvPdfName)
        val tvDate: TextView = view.findViewById(R.id.tvPdfDate)
        val btnDownload: ImageButton = view.findViewById(R.id.btnDownloadPdf)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PdfViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_pdf, parent, false)
        return PdfViewHolder(view)
    }

    override fun onBindViewHolder(holder: PdfViewHolder, position: Int) {
        val pdf = pdfList[position]
        holder.tvName.text = pdf.fileName
        
        val date = Date(pdf.timestamp)
        val format = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        holder.tvDate.text = format.format(date)

        holder.itemView.setOnClickListener { 
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            onItemClick(pdf) 
        }
        holder.btnDownload.setOnClickListener { 
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            onDownloadClick(pdf) 
        }
    }

    override fun getItemCount(): Int = pdfList.size
}
