package es.edgarms.weblauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import es.edgarms.weblauncher.model.Tile

@Composable
fun PageTile(name: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(
        modifier = modifier.size(size).background(Color(Tile.color(name)), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(Tile.initial(name), color = Color.White, style = MaterialTheme.typography.titleMedium)
    }
}
