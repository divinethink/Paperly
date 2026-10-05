package com.paperly.app.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.paperly.app.R

private val NEW_PHONE_STEPS = listOf(
    R.string.new_phone_step_1,
    R.string.new_phone_step_2,
    R.string.new_phone_step_3,
    R.string.new_phone_step_4,
    R.string.new_phone_step_5,
    R.string.new_phone_step_6,
)

/** "Move to a new phone": the backup-file route only, as numbered steps. Pure text, no new behaviour. */
@Composable
fun NewPhoneGuideSection() {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.padding(top = 8.dp)) {
        OutlinedButton(onClick = { expanded = !expanded }) {
            Text(stringResource(if (expanded) R.string.new_phone_hide else R.string.new_phone_show))
        }
        if (expanded) {
            Text(stringResource(R.string.new_phone_intro), Modifier.padding(top = 8.dp))
            NEW_PHONE_STEPS.forEachIndexed { index, step ->
                Text("${index + 1}. ${stringResource(step)}", Modifier.padding(top = 6.dp))
            }
            Text(
                stringResource(R.string.new_phone_note),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
