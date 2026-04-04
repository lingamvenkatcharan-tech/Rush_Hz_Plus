// core/utils/AssetUtil.kt
package com.example.rush_hz_plus.core.utils

import android.content.Context
import java.io.*

object AssetUtil {

    private var voskModelPath: String? = null

    fun getVoskModelPath(context: Context): String {
        if (voskModelPath == null) {
            val destDir = File(context.filesDir, "vosk-model-small-ko-0.22")
            if (!destDir.exists()) {
                extractAssetFolder(context, "vosk-model-small-ko-0.22", destDir.absolutePath)
            }
            voskModelPath = destDir.absolutePath
        }
        return voskModelPath!!
    }

    fun extractAssetFolder(context: Context, assetFolder: String, destinationPath: String) {
        val assetManager = context.assets
        val destinationDir = File(destinationPath)
        destinationDir.mkdirs()

        try {
            val assets = assetManager.list(assetFolder) ?: return
            for (asset in assets) {
                val currentAssetPath = "$assetFolder/$asset"
                val currentDestPath = "$destinationPath/$asset"

                val subAssets = assetManager.list(currentAssetPath)
                if (subAssets != null && subAssets.isNotEmpty()) {
                    // 하위 디렉터리인 경우 재귀 호출
                    extractAssetFolder(context, currentAssetPath, currentDestPath)
                } else {
                    // 파일인 경우 복사
                    copyAssetToFile(context, currentAssetPath, currentDestPath)
                }
            }
        } catch (e: IOException) {
            Logger.e("AssetUtil", "Assets 폴더 추출 실패: ${e.message}")
        }
    }

    private fun copyAssetToFile(context: Context, assetPath: String, destinationPath: String) {
        val inputStream = context.assets.open(assetPath)
        val file = File(destinationPath)
        file.parentFile?.mkdirs()
        val outputStream = FileOutputStream(file)

        inputStream.use { input ->
            outputStream.use { output ->
                input.copyTo(output)
            }
        }
    }
}