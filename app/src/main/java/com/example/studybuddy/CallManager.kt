package com.example.studybuddy

import android.content.Context
import android.content.Intent
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

object CallManager {
    private var callListener: ValueEventListener? = null
    private val database = FirebaseDatabase.getInstance().reference
    private var isListening = false

    fun startListening(context: Context) {
        if (isListening) return
        
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val callsRef = database.child("calls").child(currentUid)
        
        callListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (callerSnapshot in snapshot.children) {
                    val status = callerSnapshot.child("status").value as? String
                    if (status == "ringing") {
                        val type = callerSnapshot.child("type").value as? String
                        val callerName = callerSnapshot.child("callerName").value as? String
                        val callerUid = callerSnapshot.key
                        
                        val intent = Intent(context, IncomingCallActivity::class.java).apply {
                            putExtra("CALLER_UID", callerUid)
                            putExtra("CALLER_NAME", callerName)
                            putExtra("CALL_TYPE", type)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                        break // Only handle one call at a time
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        
        callsRef.addValueEventListener(callListener!!)
        isListening = true
    }

    fun stopListening() {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        callListener?.let {
            database.child("calls").child(currentUid).removeEventListener(it)
        }
        isListening = false
    }
}
