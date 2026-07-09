package com.appblish.jgallery.core.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction

/**
 * A single-field "name" dialog (spec §3/§6 "Create album", reused for §7.3 Rename): title, a text
 * field, Cancel / confirm. The confirm action is disabled until the trimmed name is non-blank, so the
 * caller never has to defend against empty input. The dialog owns only its own text; the caller
 * decides what a valid name means downstream (the storage layer rejects illegal characters).
 */
@Composable
fun NameInputDialog(
    title: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    initialValue: String = "",
    label: String = "Name",
) {
    var text by remember { mutableStateOf(initialValue) }
    val trimmed = text.trim()
    val canConfirm = trimmed.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("name_input_dialog"),
        title = { Text(text = title) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    label = { Text(label) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("name_input_field"),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (canConfirm) onConfirm(trimmed) },
                enabled = canConfirm,
                modifier = Modifier.testTag("name_input_confirm"),
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("name_input_cancel")) {
                Text("Cancel")
            }
        },
    )
}
