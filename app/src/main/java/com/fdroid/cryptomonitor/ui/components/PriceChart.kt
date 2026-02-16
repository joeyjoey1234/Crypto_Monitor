package com.fdroid.cryptomonitor.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path

@Composable
fun PriceChart(
    prices: List<Double>,
    modifier: Modifier = Modifier,
    lineColor: Color = Color(0xFF3DDC84)
) {
    if (prices.size < 2) return

    val min = prices.minOrNull() ?: return
    val max = prices.maxOrNull() ?: return
    val spread = (max - min).takeIf { it > 0.0 } ?: 1.0

    Canvas(modifier = modifier.fillMaxSize()) {
        val path = Path()

        prices.forEachIndexed { index, price ->
            val x = (index.toFloat() / (prices.lastIndex).toFloat()) * size.width
            val yRatio = ((price - min) / spread).toFloat()
            val y = size.height - (yRatio * size.height)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(path = path, color = lineColor)
        drawLine(
            color = Color(0x44888888),
            start = Offset(0f, size.height),
            end = Offset(size.width, size.height)
        )
    }
}
