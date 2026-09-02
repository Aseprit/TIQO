package com.aseprit.tiqo.feature.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aseprit.tiqo.R
import com.aseprit.tiqo.core.designsystem.component.GlassSurface
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun StatisticsRoute(viewModel: StatisticsViewModel, onBack: () -> Unit) {
    val stats by viewModel.statistics.collectAsStateWithLifecycle()
    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.close)) }
            Text(stringResource(R.string.statistics), style = MaterialTheme.typography.displaySmall)
        }
        Text(stringResource(R.string.this_week), color = MaterialTheme.colorScheme.primary)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(stringResource(R.string.completed), stats.completed.toString(), Modifier.weight(1f))
            StatCard(stringResource(R.string.scheduled), stats.scheduled.toString(), Modifier.weight(1f))
            StatCard(stringResource(R.string.completion_rate), "${stats.completionRate}%", Modifier.weight(1f))
        }
        if (stats.days.isEmpty() || stats.scheduled == 0) {
            GlassSurface(Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.statistics_empty),
                    Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            GlassSurface(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().height(190.dp).padding(18.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    stats.days.forEach { day ->
                        val fraction = if (day.scheduled == 0) 0f else day.completed.toFloat() / day.scheduled
                        Column(
                            Modifier.weight(1f).fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom,
                        ) {
                            Text("${day.completed}/${day.scheduled}", style = MaterialTheme.typography.labelMedium)
                            Spacer(Modifier.height(6.dp))
                            Box(
                                Modifier
                                    .fillMaxWidth(0.55f)
                                    .fillMaxHeight(fraction.coerceAtLeast(0.04f))
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primary),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("pl", "PL")).take(2).uppercase(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier) {
    GlassSurface(modifier) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
