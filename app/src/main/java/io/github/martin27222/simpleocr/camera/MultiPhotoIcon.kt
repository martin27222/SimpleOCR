package io.github.martin27222.simpleocr.camera

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Two photos lying over each other, the front one showing bent lines of text. */
val MultiPhotoIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "MultiPhoto",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        fun stroke(build: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit) = path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = build,
        )
        // The photo behind: only its top and left edges show.
        stroke {
            moveTo(3f, 15f)
            verticalLineTo(4.5f)
            quadTo(3f, 3f, 4.5f, 3f)
            horizontalLineTo(16f)
        }
        // The photo in front.
        stroke {
            moveTo(8.5f, 7f)
            horizontalLineTo(19.5f)
            quadTo(21f, 7f, 21f, 8.5f)
            verticalLineTo(19.5f)
            quadTo(21f, 21f, 19.5f, 21f)
            horizontalLineTo(8.5f)
            quadTo(7f, 21f, 7f, 19.5f)
            verticalLineTo(8.5f)
            quadTo(7f, 7f, 8.5f, 7f)
            close()
        }
        // Bent lines of text on it.
        stroke {
            moveTo(10f, 12.5f)
            quadTo(14f, 10.5f, 18f, 12.5f)
            moveTo(10f, 16.5f)
            quadTo(14f, 14.5f, 18f, 16.5f)
        }
    }.build()
}
