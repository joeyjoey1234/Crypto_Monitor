package com.fdroid.cryptomonitor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fdroid.cryptomonitor.data.model.AssetAnalysis
import com.fdroid.cryptomonitor.data.model.TradeAction
import java.text.NumberFormat
import java.util.Locale

@Composable
fun AssetCard(
    analysis: AssetAnalysis,
    modifier: Modifier = Modifier
) {
    val currency = NumberFormat.getCurrencyInstance(Locale.US)
    val actionColor = when (analysis.finalAction) {
        TradeAction.BUY -> Color(0xFF0B8A3D)
        TradeAction.SELL -> Color(0xFFB71C1C)
        TradeAction.HOLD -> Color(0xFFF9A825)
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(
                        text = "${analysis.asset.displayName} (${analysis.asset.symbol})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Price: ${currency.format(analysis.currentPriceUsd)}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    analysis.priceChange24hPct?.let { change ->
                        val sign = if (change >= 0) "+" else ""
                        val changeColor = if (change >= 0) Color(0xFF0B8A3D) else Color(0xFFB71C1C)
                        Text(
                            text = "24h: $sign${"%.2f".format(change)}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = changeColor
                        )
                    }
                    analysis.balance?.let { balance ->
                        Text(
                            text = "Wallet balance: ${"%.6f".format(balance)} ${analysis.asset.symbol}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Text(
                    text = analysis.finalAction.name,
                    modifier = Modifier
                        .background(actionColor, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            PriceChart(
                prices = analysis.history.map { it.priceUsd },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                lineColor = actionColor
            )

            analysis.algorithmSignals.forEach { signal ->
                Text(
                    text = "${signal.algorithm}: ${signal.action.name} - ${signal.reason}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
