package com.xiaomi.mimoclaw.ui.component

import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.html.HtmlPlugin

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    textColor: Int = 0xFF000000.toInt()
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextView(ctx).apply {
                val markwon = Markwon.builder(ctx)
                    .usePlugin(StrikethroughPlugin.create())
                    .usePlugin(TablePlugin.create(ctx))
                    .usePlugin(HtmlPlugin.create())
                    .build()
                markwon.setMarkdown(this, markdown)
                setTextColor(textColor)
                setPadding(0, 0, 0, 0)
            }
        },
        update = { textView ->
            val markwon = Markwon.builder(textView.context)
                .usePlugin(StrikethroughPlugin.create())
                .usePlugin(TablePlugin.create(textView.context))
                .usePlugin(HtmlPlugin.create())
                .build()
            markwon.setMarkdown(textView, markdown)
            textView.setTextColor(textColor)
        }
    )
}
