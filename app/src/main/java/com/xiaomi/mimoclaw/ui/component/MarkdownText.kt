package com.xiaomi.mimoclaw.ui.component

import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.syntax.Prism4jThemeDarkula
import io.noties.markwon.syntax.SyntaxHighlightPlugin
import io.noties.prism4j.Prism4j
import io.noties.prism4j.annotations.PrismBundle

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    textColor: Int = 0xFF000000.toInt()
) {
    val context = LocalContext.current

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextView(ctx).apply {
                // Build Markwon with plugins
                val prism4j = Prism4j(object : Prism4j.GrammarLocator {
                    override fun grammar(prism4j: Prism4j, language: String) = when (language) {
                        "kotlin", "kt" -> prism4j.grammar("clike")
                        "java" -> prism4j.grammar("clike")
                        "python", "py" -> prism4j.grammar("python")
                        "javascript", "js" -> prism4j.grammar("javascript")
                        "json" -> prism4j.grammar("json")
                        "xml" -> prism4j.grammar("markup")
                        "html" -> prism4j.grammar("markup")
                        "css" -> prism4j.grammar("css")
                        "bash", "sh" -> prism4j.grammar("bash")
                        "sql" -> prism4j.grammar("sql")
                        else -> prism4j.grammar("clike")
                    }
                    override fun languages() = listOf(
                        "clike", "python", "javascript", "json", "markup", "css", "bash", "sql"
                    )
                })

                val markwon = Markwon.builder(ctx)
                    .usePlugin(StrikethroughPlugin.create())
                    .usePlugin(TablePlugin.create(ctx))
                    .usePlugin(HtmlPlugin.create())
                    .usePlugin(SyntaxHighlightPlugin.create(prism4j, Prism4jThemeDarkula.create()))
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
