package media.quaternion.qmetronome.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import media.quaternion.qmetronome.ui.theme.PureWhite
import media.quaternion.qmetronome.ui.theme.QmNavy

/**
 * The only deliberate departure from strict monochrome: a small Quaternion Media credit mark.
 * There's no official QM logo asset to draw from, so this is restrained typography rather than
 * an invented graphic - navy fill, white text, nothing else borrows the color.
 */
@Composable
fun BrandFooter(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier
                .background(QmNavy, RoundedCornerShape(2.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Text(
                text = "QM",
                color = PureWhite,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Text(
            text = "qMetronome",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}
