package io.github.martin27222.simpleocr.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.martin27222.simpleocr.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    settings: AppSettings,
    onChange: (AppSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.settings),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            SettingRow(
                title = stringResource(R.string.setting_auto_dewarp),
                description = stringResource(R.string.setting_auto_dewarp_description),
                checked = settings.autoDewarp,
                onCheckedChange = { onChange(settings.copy(autoDewarp = it)) },
            )
            SettingRow(
                title = stringResource(R.string.setting_join_hyphenated),
                description = stringResource(R.string.setting_join_hyphenated_description),
                checked = settings.joinHyphenatedWords,
                onCheckedChange = { onChange(settings.copy(joinHyphenatedWords = it)) },
            )
            SettingRow(
                title = stringResource(R.string.setting_flowing_text),
                description = stringResource(R.string.setting_flowing_text_description),
                checked = settings.flowingText,
                onCheckedChange = { onChange(settings.copy(flowingText = it)) },
            )
            SettingRow(
                title = stringResource(R.string.setting_auto_copy),
                description = stringResource(R.string.setting_auto_copy_description),
                checked = settings.autoCopy,
                onCheckedChange = { onChange(settings.copy(autoCopy = it)) },
            )
            SettingRow(
                title = stringResource(R.string.setting_auto_save),
                description = stringResource(R.string.setting_auto_save_description),
                checked = settings.autoSavePhotos,
                onCheckedChange = { onChange(settings.copy(autoSavePhotos = it)) },
            )
            SettingRow(
                title = stringResource(R.string.setting_light_on),
                description = stringResource(R.string.setting_light_on_description),
                checked = settings.lightOnAtStart,
                onCheckedChange = { onChange(settings.copy(lightOnAtStart = it)) },
            )
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // The whole row is the switch; the Switch itself only shows the state.
        Switch(checked = checked, onCheckedChange = null)
    }
}
