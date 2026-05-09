package com.example.studybuddy

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.EditText
import android.widget.Toast
import java.util.Locale

class SpeechToTextHelper(private val context: Context, private val editText: EditText) {

    private var speechRecognizer: SpeechRecognizer? = null
    private val recognitionIntent: Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
    }

    init {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Toast.makeText(context, "Listening...", Toast.LENGTH_SHORT).show()
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    val message = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                        SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                        SpeechRecognizer.ERROR_NETWORK -> "Network error"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                        SpeechRecognizer.ERROR_NO_MATCH -> "No match found"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech service busy"
                        SpeechRecognizer.ERROR_SERVER -> "Server error"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                        else -> "Didn't understand, please try again."
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }

                override fun onResults(results: Bundle?) {
                    val data = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!data.isNullOrEmpty()) {
                        val currentText = editText.text.toString()
                        val recognizedText = data[0]
                        if (currentText.isEmpty()) {
                            editText.setText(recognizedText)
                        } else {
                            editText.setText("$currentText $recognizedText")
                        }
                        editText.setSelection(editText.text.length)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    fun startListening() {
        speechRecognizer?.startListening(recognitionIntent)
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
    }

    fun destroy() {
        speechRecognizer?.destroy()
    }
}
