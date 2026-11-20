package tv.porch.app.ui.loop

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import coil.imageLoader
import coil.network.HttpException
import coil.request.ErrorResult
import coil.request.SuccessResult
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Scale
import coil.size.Size
import tv.porch.app.data.PosterRatings
import tv.porch.app.data.ShowPoster
import tv.porch.app.data.ShowPosters
import tv.porch.app.domain.Rpdb
import tv.porch.app.domain.ShowLookup
import tv.porch.app.ui.components.LogoTile
import tv.porch.app.ui.theme.PorchColors
import tv.porch.app.ui.theme.PorchRadius
import tv.porch.app.ui.theme.PorchSpace
import tv.porch.app.ui.theme.PorchType

private val NamePad = 8.dp

/** What came back from RPDB for one tile: its picture, or nothing usable. */
private sealed interface Rating {
    class Got(val picture: ImageBitmap) : Rating
    data object Nothing : Rating
}

/**
 * What a 24/7 tile shows: with a rating key, the RPDB poster; else the show's TVmaze poster once it has one; else the
 * channel's own logo when it has a real one; else the show's name on a solid tile. Never the provider's generic logo.
 * Posters are asked for while the tile is on screen; leaving the screen cancels the ask, which drops it out of the queue.
 */
@Composable
fun PosterFace(item: LoopItem, posters: ShowPosters, ratings: PosterRatings) {
    val search = remember(item.name) { ShowLookup.searchText(item.name) }
    val keyed by ratings.on.collectAsStateWithLifecycle()
    // Not reset when ratings switch on: the saved poster stays up while the ids are fetched.
    var found by remember(search) { mutableStateOf<ShowPoster?>(null) }
    LaunchedEffect(search, keyed) {
        // The saved poster shows at once. Ids are fetched after it and may fail without taking it away.
        found = posters.poster(search)
        if (keyed) {
            val withId = posters.poster(search, wantId = true)
            if (withId.url != null || withId.imdb != null) found = withId
        }
    }
    // The address holds the key, so it stays in this function and is never shown or logged.
    val ratingAddress = found?.imdb?.let { ratings.addressFor(it) }
    var rating by remember(ratingAddress) { mutableStateOf<Rating?>(null) }
    if (ratingAddress != null) {
        val context = LocalContext.current
        val density = LocalDensity.current
        val w = with(density) { PorchSpace.LoopPosterWidth.roundToPx() }
        val h = with(density) { PorchSpace.LoopPosterHeight.roundToPx() }
        LaunchedEffect(ratingAddress) { rating = fetchRating(context, ratingAddress, found?.imdb.orEmpty(), w, h, ratings) }
    }
    val picture = (rating as? Rating.Got)?.picture
    val tvmaze = found?.url
    when {
        picture != null -> Box(Modifier.size(PorchSpace.LoopPosterWidth, PorchSpace.LoopPosterHeight).clip(RoundedCornerShape(PorchRadius.Tile))) {
            Image(picture, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        // RPDB is being asked: hold a solid block rather than flash the TVmaze poster and swap it.
        ratingAddress != null && rating == null -> SolidBlock()
        tvmaze != null -> PosterImage(tvmaze, item.name)
        item.logo != null -> LogoTile(item.channel, PorchSpace.LoopPosterWidth, PorchSpace.LoopPosterHeight)
        else -> NameTile(item.name)
    }
}

@Composable
private fun SolidBlock() {
    Box(
        Modifier
            .size(PorchSpace.LoopPosterWidth, PorchSpace.LoopPosterHeight)
            .background(PorchColors.Surface, RoundedCornerShape(PorchRadius.Tile)),
    )
}

/**
 * Asks the shared image loader for the RPDB poster, decoded at tile size and kept on disk. Anything unusable (a refusal,
 * a miss, a network error, a tiny or single-colour picture) is Nothing and is remembered for the session. Nothing here
 * logs or keeps the address, and the error is never read for its message.
 */
private suspend fun fetchRating(context: Context, address: String, imdb: String, w: Int, h: Int, ratings: PosterRatings): Rating {
    val request = ImageRequest.Builder(context)
        .data(address)
        .size(Size(w, h))
        .scale(Scale.FILL)
        .precision(Precision.INEXACT)
        .allowHardware(false) // Read once to spot a blank picture.
        .crossfade(false)
        .memoryCachePolicy(CachePolicy.ENABLED)
        .diskCachePolicy(CachePolicy.ENABLED)
        .build()
    when (val result = context.imageLoader.execute(request)) {
        is SuccessResult -> {
            val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
            val blank = bitmap == null || Rpdb.looksBlank(bitmap.width, sample(bitmap))
            if (bitmap == null || blank) {
                ratings.noPoster(imdb)
                return Rating.Nothing
            }
            ratings.gotPoster()
            return Rating.Got(bitmap.asImageBitmap())
        }
        is ErrorResult -> {
            when ((result.throwable as? HttpException)?.response?.code) {
                403 -> ratings.refused(imdb)
                404 -> ratings.noPoster(imdb)
                else -> ratings.failed(imdb)
            }
            return Rating.Nothing
        }
    }
}

/** Up to about 400 pixels spread over the picture. */
private fun sample(bitmap: Bitmap): IntArray {
    val steps = 20
    val out = IntArray(steps * steps)
    for (y in 0 until steps) for (x in 0 until steps) {
        out[y * steps + x] = bitmap.getPixel(
            (x * bitmap.width / steps).coerceAtMost(bitmap.width - 1),
            (y * bitmap.height / steps).coerceAtMost(bitmap.height - 1),
        )
    }
    return out
}

@Composable
private fun PosterImage(address: String, name: String) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val w = with(density) { PorchSpace.LoopPosterWidth.roundToPx() }
    val h = with(density) { PorchSpace.LoopPosterHeight.roundToPx() }
    val request = remember(address, w, h) {
        ImageRequest.Builder(context)
            .data(address)
            .size(Size(w, h))
            .scale(Scale.FILL)
            .precision(Precision.INEXACT)
            .crossfade(false)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
    }
    val painter = rememberAsyncImagePainter(model = request, contentScale = ContentScale.Crop)
    val shape = RoundedCornerShape(PorchRadius.Tile)
    if (painter.state is AsyncImagePainter.State.Error) {
        NameTile(name)
        return
    }
    // A solid block until the picture is ready, so a slow poster never reads as broken.
    Box(Modifier.size(PorchSpace.LoopPosterWidth, PorchSpace.LoopPosterHeight).clip(shape).background(PorchColors.Surface)) {
        if (painter.state is AsyncImagePainter.State.Success) {
            Image(painter, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}

@Composable
private fun NameTile(name: String) {
    Box(
        Modifier
            .size(PorchSpace.LoopPosterWidth, PorchSpace.LoopPosterHeight)
            .background(PorchColors.Surface, RoundedCornerShape(PorchRadius.Tile))
            .padding(NamePad),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name,
            style = PorchType.TileTitle,
            color = PorchColors.Text,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
