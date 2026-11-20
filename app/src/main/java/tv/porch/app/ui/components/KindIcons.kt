package tv.porch.app.ui.components

import androidx.annotation.DrawableRes
import tv.porch.app.R
import tv.porch.app.domain.Kind

/** The icon for a kind of channel. Icons are white vectors, tinted by whatever they sit in. */
@DrawableRes
fun Kind.iconRes(): Int = when (this) {
    Kind.Sport -> R.drawable.ic_sport
    Kind.News -> R.drawable.ic_news
    Kind.Movies -> R.drawable.ic_movies
    Kind.Kids -> R.drawable.ic_kids
    Kind.Music -> R.drawable.ic_music
    Kind.Docs -> R.drawable.ic_docs
}
