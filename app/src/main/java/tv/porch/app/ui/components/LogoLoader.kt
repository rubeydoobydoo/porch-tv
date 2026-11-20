package tv.porch.app.ui.components

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * The one image loader for channel logos and programme pictures, built once by the Application. A Chromecast has little memory and a slow
 * GPU, so everything is small and capped. No logger is set, because an image address can carry a login.
 */
object LogoLoader {
    const val MEMORY_BYTES = 20L * 1024 * 1024
    const val DISK_BYTES = 100L * 1024 * 1024
    const val TIMEOUT_SECONDS = 5L
    const val PARALLEL = 4

    fun create(context: Context): ImageLoader {
        val dispatcher = Dispatcher().apply {
            maxRequests = PARALLEL
            maxRequestsPerHost = PARALLEL
        }
        val client = OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
        return ImageLoader.Builder(context)
            .okHttpClient(client)
            .memoryCache { MemoryCache.Builder(context).maxSizeBytes(MEMORY_BYTES.toInt()).build() }
            .diskCache { DiskCache.Builder().directory(context.cacheDir.resolve("logos")).maxSizeBytes(DISK_BYTES).build() }
            .respectCacheHeaders(false)
            .crossfade(false)
            .build()
    }
}
