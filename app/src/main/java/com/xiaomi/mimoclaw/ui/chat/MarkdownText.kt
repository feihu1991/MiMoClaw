package com.xiaomi.mimoclaw.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Markdown 文本渲染组件
 *
 * 支持：
 * - 标题 (# ~ ######)
 * - 粗体 (**text**)
 * - 斜体 (*text*)
 * - 行内代码 (`code`)
 * - 代码块 (```lang ... ```)
 * - 链接 [text](url)
 * - 无序列表 (- item)
 * - 有序列表 (1. item)
 * - 引用 (> text)
 * - 分割线 (---)
 * - 删除线 (~~text~~)
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: TextStyle = MaterialTheme.typography.bodyMedium
) {
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }

    SelectionContainer {
        Column(modifier = modifier) {
            blocks.forEach { block ->
                when (block) {
                    is MdBlock.Header -> HeaderBlock(block, color)
                    is MdBlock.CodeBlock -> CodeBlock(block)
                    is MdBlock.Quote -> QuoteBlock(block, color, style)
                    is MdBlock.ListBlock -> ListBlock(block, color, style)
                    is MdBlock.HorizontalRule -> HorizontalRuleBlock()
                    is MdBlock.Paragraph -> ParagraphBlock(block, color, style)
                }
            }
        }
    }
}

// ── Block Composables ──

@Composable
private fun HeaderBlock(block: MdBlock.Header, baseColor: Color) {
    val (fontSize, fontWeight) = when (block.level) {
        1 -> 24.sp to FontWeight.Bold
        2 -> 20.sp to FontWeight.Bold
        3 -> 18.sp to FontWeight.SemiBold
        4 -> 16.sp to FontWeight.SemiBold
        5 -> 14.sp to FontWeight.Medium
        else -> 13.sp to FontWeight.Medium
    }
    Text(
        text = parseInlineMarkdown(block.text),
        fontSize = fontSize,
        fontWeight = fontWeight,
        color = baseColor,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun CodeBlock(block: MdBlock.CodeBlock) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .horizontalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        // 语言标签
        block.language?.let { lang ->
            Text(
                text = lang,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        Text(
            text = block.code,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 18.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun QuoteBlock(block: MdBlock.Quote, baseColor: Color, style: TextStyle) {
    Row(modifier = Modifier.padding(vertical = 4.dp)) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = parseInlineMarkdown(block.text),
            style = style,
            color = baseColor.copy(alpha = 0.8f),
            fontStyle = FontStyle.Italic
        )
    }
}

@Composable
private fun ListBlock(block: MdBlock.ListBlock, baseColor: Color, style: TextStyle) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        block.items.forEachIndexed { index, item ->
            Row(modifier = Modifier.padding(vertical = 2.dp)) {
                val bullet = if (block.ordered) "${index + 1}." else "•"
                Text(
                    text = bullet,
                    style = style,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(24.dp)
                )
                Text(
                    text = parseInlineMarkdown(item),
                    style = style,
                    color = baseColor
                )
            }
        }
    }
}

@Composable
private fun HorizontalRuleBlock() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    )
}

@Composable
private fun ParagraphBlock(block: MdBlock.Paragraph, baseColor: Color, style: TextStyle) {
    val uriHandler = LocalUriHandler.current
    val annotatedString = parseInlineMarkdown(block.text)

    // 检测是否有链接，决定用 Text 还是 ClickableText
    val hasLinks = annotatedString.getStringAnnotations("URL", 0, annotatedString.length).isNotEmpty()

    if (hasLinks) {
        ClickableText(
            text = annotatedString,
            style = style.copy(color = baseColor),
            onClick = { offset ->
                annotatedString.getStringAnnotations("URL", offset, offset)
                    .firstOrNull()?.let { uriHandler.openUri(it.item) }
            },
            modifier = Modifier.padding(vertical = 2.dp)
        )
    } else {
        Text(
            text = annotatedString,
            style = style,
            color = baseColor,
            modifier = Modifier.padding(vertical = 2.dp)
        )
    }
}

// ── Block Parser ──

private sealed class MdBlock {
    data class Header(val level: Int, val text: String) : MdBlock()
    data class CodeBlock(val language: String?, val code: String) : MdBlock()
    data class Quote(val text: String) : MdBlock()
    data class ListBlock(val ordered: Boolean, val items: List<String>) : MdBlock()
    data object HorizontalRule : MdBlock()
    data class Paragraph(val text: String) : MdBlock()
}

private fun parseMarkdownBlocks(markdown: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val lines = markdown.lines()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]

        // 空行跳过
        if (line.isBlank()) {
            i++
            continue
        }

        // 代码块
        if (line.trimStart().startsWith("```")) {
            val language = line.trimStart().removePrefix("```").trim().ifEmpty { null }
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                codeLines.add(lines[i])
                i++
            }
            if (i < lines.size) i++ // 跳过结束的 ```
            blocks.add(MdBlock.CodeBlock(language, codeLines.joinToString("\n")))
            continue
        }

        // 标题
        val headerMatch = Regex("^(#{1,6})\\s+(.+)").matchEntire(line)
        if (headerMatch != null) {
            blocks.add(MdBlock.Header(headerMatch.groupValues[1].length, headerMatch.groupValues[2]))
            i++
            continue
        }

        // 分割线
        if (line.trim().matches(Regex("^[-*_]{3,}$"))) {
            blocks.add(MdBlock.HorizontalRule)
            i++
            continue
        }

        // 引用
        if (line.trimStart().startsWith("> ")) {
            val quoteLines = mutableListOf<String>()
            while (i < lines.size && lines[i].trimStart().startsWith("> ")) {
                quoteLines.add(lines[i].trimStart().removePrefix("> "))
                i++
            }
            blocks.add(MdBlock.Quote(quoteLines.joinToString("\n")))
            continue
        }

        // 无序列表
        if (line.trimStart().matches(Regex("^[-*+]\\s+.*"))) {
            val items = mutableListOf<String>()
            while (i < lines.size && lines[i].trimStart().matches(Regex("^[-*+]\\s+.*"))) {
                items.add(lines[i].trimStart().replaceFirst(Regex("^[-*+]\\s+"), ""))
                i++
            }
            blocks.add(MdBlock.ListBlock(ordered = false, items = items))
            continue
        }

        // 有序列表
        if (line.trimStart().matches(Regex("^\\d+\\.\\s+.*"))) {
            val items = mutableListOf<String>()
            while (i < lines.size && lines[i].trimStart().matches(Regex("^\\d+\\.\\s+.*"))) {
                items.add(lines[i].trimStart().replaceFirst(Regex("^\\d+\\.\\s+"), ""))
                i++
            }
            blocks.add(MdBlock.ListBlock(ordered = true, items = items))
            continue
        }

        // 普通段落（合并连续非空行）
        val paragraphLines = mutableListOf<String>()
        while (i < lines.size && lines[i].isNotBlank()) {
            // 如果遇到特殊行则停止
            val l = lines[i].trimStart()
            if (l.startsWith("```") || l.startsWith("# ") || l.matches(Regex("^#{1,6}\\s+.*")) ||
                l.startsWith("> ") || l.matches(Regex("^[-*+]\\s+.*")) ||
                l.matches(Regex("^\\d+\\.\\s+.*")) || l.matches(Regex("^[-*_]{3,}$"))
            ) break
            paragraphLines.add(lines[i])
            i++
        }
        if (paragraphLines.isNotEmpty()) {
            blocks.add(MdBlock.Paragraph(paragraphLines.joinToString("\n")))
        }
    }

    return blocks
}

// ── Inline Parser ──

/**
 * 解析行内 Markdown（粗体、斜体、行内代码、链接、删除线）
 * 返回 AnnotatedString
 */
private fun parseInlineMarkdown(text: String): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            // 行内代码
            if (text[i] == '`' && (i == 0 || text[i - 1] != '\\')) {
                val end = text.indexOf('`', i + 1)
                if (end > i) {
                    val code = text.substring(i + 1, end)
                    withStyle(SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        background = Color(0x20808080)
                    )) { append(code) }
                    i = end + 1
                    continue
                }
            }

            // 链接 [text](url)
            if (text[i] == '[') {
                val linkMatch = Regex("^\\[([^]]+)]\\(([^)]+)\\)").find(text.substring(i))
                if (linkMatch != null) {
                    val linkText = linkMatch.groupValues[1]
                    val url = linkMatch.groupValues[2]
                    withStyle(SpanStyle(
                        color = Color(0xFF1A73E8),
                        textDecoration = TextDecoration.Underline
                    )) {
                        pushStringAnnotation("URL", url)
                        append(linkText)
                        pop()
                    }
                    i += linkMatch.range.last + 1
                    continue
                }
            }

            // 删除线 ~~text~~
            if (i + 1 < text.length && text[i] == '~' && text[i + 1] == '~') {
                val end = text.indexOf("~~", i + 2)
                if (end > i) {
                    withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                    continue
                }
            }

            // 粗体 **text**
            if (i + 1 < text.length && text[i] == '*' && text[i + 1] == '*') {
                val end = text.indexOf("**", i + 2)
                if (end > i) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                    continue
                }
            }

            // 斜体 *text* (不匹配 **)
            if (text[i] == '*' && (i + 1 < text.length && text[i + 1] != '*')) {
                val end = text.indexOf('*', i + 1)
                if (end > i && (end + 1 >= text.length || text[end + 1] != '*')) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                    continue
                }
            }

            // 普通字符
            append(text[i])
            i++
        }
    }
}
