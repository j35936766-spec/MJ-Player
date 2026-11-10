package com.mj.player

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

data class MediaItemInfo(
    val uri: Uri,
    val name: String,
    val durationMs: Long
)

object MediaRepository {

    fun loadVideos(context: Context): List<MediaItemInfo> =
        query(context, MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATE_ADDED)

    fun loadAudios(context: Context): List<MediaItemInfo> =
        query(context, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED)

    private fun query(
        context: Context, collection: Uri,
        idCol: String, nameCol: String, durCol: String, dateCol: String
    ): List<MediaItemInfo> {
        val list = mutableListOf<MediaItemInfo>()
        context.contentResolver.query(
            collection, arrayOf(idCol, nameCol, durCol), null, null, "$dateCol DESC"
        )?.use { c ->
            val i = c.getColumnIndexOrThrow(idCol)
            val n = c.getColumnIndexOrThrow(nameCol)
            val d = c.getColumnIndexOrThrow(durCol)
            while (c.moveToNext()) {
                list += MediaItemInfo(
                    uri = ContentUris.withAppendedId(collection, c.getLong(i)),
                    name = c.getString(n) ?: "Unknown",
                    durationMs = c.getLong(d)
                )
            }
        }
        return list
    }
}
