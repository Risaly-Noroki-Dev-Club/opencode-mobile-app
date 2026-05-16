package dev.opencode.mobile.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import dev.opencode.mobile.R

@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        parseMarkdownBlocks(text).forEachIndexed { index, block ->
            if (index > 0) Spacer(modifier = Modifier.height(8.dp))
            when (block) {
                is MarkdownBlock.Code -> CodeBlock(block.code, block.language, block.closed)
                is MarkdownBlock.Heading -> Text(
                    text = block.text,
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.h6
                        2 -> MaterialTheme.typography.subtitle1
                        else -> MaterialTheme.typography.subtitle2
                    },
                    fontWeight = FontWeight.SemiBold,
                )
                is MarkdownBlock.Quote -> Text(
                    text = block.text,
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.72f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colors.onSurface.copy(alpha = 0.06f))
                        .padding(12.dp),
                )
                is MarkdownBlock.ListItems -> Column {
                    block.items.forEach { item ->
                        Text(text = item.renderedText, style = MaterialTheme.typography.body2)
                    }
                }
                is MarkdownBlock.Paragraph -> Text(text = inlineMarkdown(block.text), style = MaterialTheme.typography.body2)
            }
        }
    }
}

@Composable
private fun CodeBlock(code: String, language: String?, closed: Boolean) {
    val clipboard = LocalClipboardManager.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colors.onSurface.copy(alpha = 0.10f))
            .padding(12.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = if (closed) language?.ifBlank { null } ?: "code" else "${language?.ifBlank { null } ?: "code"} · streaming",
                style = MaterialTheme.typography.overline,
                color = MaterialTheme.colors.primary,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { clipboard.setText(AnnotatedString(code.trimEnd())) }) {
                Text(stringResource(R.string.copy_button))
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        if (language.equals("diff", ignoreCase = true)) {
            Column(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                code.trimEnd().lines().forEach { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.caption,
                        fontFamily = FontFamily.Monospace,
                        color = diffLineColor(line),
                    )
                }
            }
        } else {
            Text(
                text = code.trimEnd(),
                style = MaterialTheme.typography.caption,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            )
        }
    }
}

@Composable
private fun diffLineColor(line: String): Color = when {
    line.startsWith("+") && !line.startsWith("+++") -> Color(0xFF2E7D32)
    line.startsWith("-") && !line.startsWith("---") -> Color(0xFFC62828)
    line.startsWith("@@") -> MaterialTheme.colors.primary
    else -> MaterialTheme.colors.onSurface
}

private sealed interface MarkdownBlock {
    data class Paragraph(val text: String) : MarkdownBlock
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class ListItems(val items: List<ListItem>) : MarkdownBlock
    data class Quote(val text: String) : MarkdownBlock
    data class Code(val code: String, val language: String?, val closed: Boolean) : MarkdownBlock
}

private data class ListItem(val marker: String, val text: String) {
    val renderedText: String = "$marker $text"
}

private fun parseMarkdownBlocks(text: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val paragraph = mutableListOf<String>()
    val listItems = mutableListOf<ListItem>()
    val quote = mutableListOf<String>()
    var inCode = false
    var codeLanguage: String? = null
    val code = StringBuilder()

    fun flushParagraph() {
        if (paragraph.isNotEmpty()) {
            blocks += MarkdownBlock.Paragraph(paragraph.joinToString("\n"))
            paragraph.clear()
        }
    }
    fun flushList() {
        if (listItems.isNotEmpty()) {
            blocks += MarkdownBlock.ListItems(listItems.toList())
            listItems.clear()
        }
    }
    fun flushQuote() {
        if (quote.isNotEmpty()) {
            blocks += MarkdownBlock.Quote(quote.joinToString("\n"))
            quote.clear()
        }
    }
    fun flushInline() {
        flushParagraph()
        flushList()
        flushQuote()
    }

    text.lines().forEach { line ->
        val trimmed = line.trimStart()
        if (trimmed.startsWith("```")) {
            if (inCode) {
                blocks += MarkdownBlock.Code(code.toString(), codeLanguage, closed = true)
                code.clear()
                codeLanguage = null
                inCode = false
            } else {
                flushInline()
                codeLanguage = trimmed.removePrefix("```").takeIf { it.isNotBlank() }
                inCode = true
            }
            return@forEach
        }

        if (inCode) {
            code.appendLine(line)
            return@forEach
        }

        if (line.isBlank()) {
            flushInline()
            return@forEach
        }

        val headingLevel = trimmed.takeWhile { it == '#' }.length
        if (headingLevel in 1..3 && trimmed.getOrNull(headingLevel) == ' ') {
            flushInline()
            blocks += MarkdownBlock.Heading(headingLevel, trimmed.drop(headingLevel + 1))
            return@forEach
        }

        parseListItem(trimmed)?.let { item ->
            flushParagraph()
            flushQuote()
            listItems += item
            return@forEach
        }

        if (trimmed.startsWith("> ")) {
            flushParagraph()
            flushList()
            quote += trimmed.drop(2)
            return@forEach
        }

        flushList()
        flushQuote()
        paragraph += line
    }

    if (inCode) blocks += MarkdownBlock.Code(code.toString(), codeLanguage, closed = false)
    flushInline()
    return blocks.ifEmpty { listOf(MarkdownBlock.Paragraph(text)) }
}

private fun parseListItem(line: String): ListItem? {
    if (line.startsWith("- ") || line.startsWith("* ")) return ListItem("•", line.drop(2))
    val match = Regex("^(\\d+)[.)]\\s+(.+)$").find(line) ?: return null
    return ListItem("${match.groupValues[1]}.", match.groupValues[2])
}

private fun inlineMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end > i) {
                    val start = length
                    append(text.substring(i + 2, end))
                    addStyle(SpanStyle(fontWeight = FontWeight.SemiBold), start, length)
                    i = end + 2
                } else {
                    append(text[i])
                    i++
                }
            }
            text[i] == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end > i) {
                    val start = length
                    append(text.substring(i + 1, end))
                    addStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0x1F000000)), start, length)
                    i = end + 1
                } else {
                    append(text[i])
                    i++
                }
            }
            text[i] == '[' -> {
                val close = text.indexOf("](", i)
                val end = if (close > i) text.indexOf(')', close + 2) else -1
                if (close > i && end > close) {
                    val label = text.substring(i + 1, close)
                    val start = length
                    append(label)
                    addStyle(SpanStyle(color = Color(0xFF003D99), textDecoration = TextDecoration.Underline), start, length)
                    i = end + 1
                } else {
                    append(text[i])
                    i++
                }
            }
            else -> {
                append(text[i])
                i++
            }
        }
    }
}
