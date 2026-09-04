package com.example.feedsense.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore

/*
 * Reusable pickers for the annotation workflow (Milestone
 * 8A-2). All options are sourced from AnnotationTaxonomy by
 * the caller so the UI never hardcodes a second taxonomy.
 */

/*
 * A label + read-only OutlinedTextField opening a dropdown.
 */
@Composable
fun LabeledDropdown(
    label: String,
    value: String?,
    options: List<String>,
    display: (String) -> String = { it },
    placeholder: String = "Select $label",
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value?.let(display) ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            trailingIcon = {
                IconButton(onClick = { expanded = true }) {
                    Icon(
                        Icons.Default.ExpandMore,
                        contentDescription = "Select $label",
                        modifier = Modifier.width(20.dp)
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            display(option),
                            maxLines = 1
                        )
                    },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

/*
 * A 3-state toggle for tri-state fields.
 *
 *   null (UNKNOWN) -> true (YES) -> false (NO) -> null ...
 *
 * Defaults to UNKNOWN and NEVER auto-fills: the evaluator
 * must consciously tap to assert YES or NO. For interaction
 * signals especially, "not seen" stays UNKNOWN rather than
 * being treated as false, preserving honesty.
 */
@Composable
fun TriStateToggle(
    label: String,
    value: Boolean?,
    modifier: Modifier = Modifier,
    onCycle: () -> Unit
) {
    val stateLabel = when (value) {
        true -> "YES"
        false -> "NO"
        null -> "UNKNOWN"
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        OutlinedButton(
            onClick = onCycle,
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(stateLabel)
        }
    }
}

/*
 * A labeled section spacer to visually separate groups of
 * annotation fields.
 */
@Composable
fun FieldGroupSpacer(label: String) {
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary
    )
    Spacer(modifier = Modifier.height(6.dp))
}