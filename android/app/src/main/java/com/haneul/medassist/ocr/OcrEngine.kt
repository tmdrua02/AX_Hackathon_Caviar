package com.haneul.medassist.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

interface OcrEngine {
    suspend fun recognize(images: List<Uri>): String
}

@Singleton
class MlKitKoreanOcrEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) : OcrEngine {
    private val recognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())

    override suspend fun recognize(images: List<Uri>): String {
        val results = mutableListOf<String>()
        for (uri in images) {
            results += recognizer.process(InputImage.fromFilePath(context, uri)).await().text
        }
        return results.joinToString("\n")
    }
}
