package es.edgarms.weblauncher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import es.edgarms.weblauncher.icons.PageIcons
import es.edgarms.weblauncher.model.Page
import es.edgarms.weblauncher.model.Tile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The page's icon, or its generated tile while there is none. */
@Composable
fun PageIcon(page: Page, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(initialValue = null, page.iconPath) {
        value = withContext(Dispatchers.IO) { PageIcons.load(context, page.iconPath)?.asImageBitmap() }
    }
    IconOrTile(image, page.name, modifier, size)
}

@Composable
fun IconOrTile(image: ImageBitmap?, name: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    if (image != null) {
        Image(
            image,
            contentDescription = null,
            modifier = modifier.size(size).clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
    } else {
        PageTile(name, modifier, size)
    }
}

@Composable
fun PageTile(name: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(
        modifier = modifier.size(size).background(Color(Tile.color(name)), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(Tile.initial(name), color = Color.White, style = MaterialTheme.typography.titleMedium)
    }
}
