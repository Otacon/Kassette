package frontend.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog as BasicDialog

@Composable
fun Dialog(
    title: String,
    onPositive: () -> Unit,
    onNegative: (() -> Unit)? = null,
    onDismissRequest: () -> Unit = onNegative ?: onPositive,
    positiveText: String = "OK",
    negativeText: String? = "Cancel",
    content: @Composable ColumnScope.() -> Unit,
) {
    BasicDialog(
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier
                .width(400.dp)
                .background(Color.LightGray)
        ) {
            BasicText(
                modifier = Modifier.padding(all = 16.dp),
                text = title
            )

            Spacer(Modifier.height(16.dp))

            content()

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp, horizontal = 16.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                if (onNegative != null && negativeText != null) {
                    Button(
                        text = negativeText,
                        onClick = onNegative,
                    )

                    Spacer(Modifier.width(8.dp))
                }

                Button(
                    text = positiveText,
                    onClick = onPositive,
                )
            }
        }
    }
}
