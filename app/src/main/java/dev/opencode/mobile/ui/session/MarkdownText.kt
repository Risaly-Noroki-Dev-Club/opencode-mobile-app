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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import dev.opencode.mobile.R
import androidx.compose.ui.res.stringResource

@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        parseMarkdownBlocks(text).forEachIndexed { index, block ->
            if (index > 0) Spacer(modifier = Modifier.height(8.dp))
            when (block) {
                is MarkdownBlock.Code -> CodeBlock(block.code, block.language)
                is MarkdownBlock.Heading -> Text(
                    text = block.text,
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.subtitle1
                        2 -> MaterialTheme.typography.subtitle2
                        else -> MaterialTheme.typography.overline
                    },
                    fontWeight = FontWeight.SemiBold,
                )
                is MarkdownBlock.Quote -> Text(
                    text = block.text,
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colors.onSurface.copy(alpha = 0.05f))
                        .padding(12.dp),
                )
                is MarkdownBlock.ListItems -> Column {
                    block.items.forEach { item ->
                        Text(text = "• $item", style = MaterialTheme.typography.body2)
                    }
                }
                is MarkdownBlock.Paragraph -> Text(text = block.text, style = MaterialTheme.typography.body2)
            }
        }
    }
}

@Composable
private fun CodeBlock(code: String, language: String?) {
    val clipboard = LocalClipboardManager.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colors.onSurface.copy(alpha = 0.12f))
            .padding(12.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = language?.ifBlank { null } ?: "code",
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
    data class ListItems(val items: List<String>) : MarkdownBlock
    data class Quote(val text: String) : MarkdownBlock
    data class Code(val code: String, val language: String?) : MarkdownBlock
}

private fun parseMarkdownBlocks(text: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val paragraph = mutableListOf<String>()
    val listItems = mutableListOf<String>()
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
        if (line.startsWith("```")) {
            if (inCode) {
                blocks += MarkdownBlock.Code(code.toString(), codeLanguage)
                code.clear()
                codeLanguage = null
                inCode = false
            } else {
                flushInline()
                codeLanguage = line.removePrefix("```").takeIf { it.isNotBlank() }
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

        val headingLevel = line.takeWhile { it == '#' }.length
        if (headingLevel in 1..3 && line.getOrNull(headingLevel) == ' ') {
            flushInline()
            blocks += MarkdownBlock.Heading(headingLevel, line.drop(headingLevel + 1))
            return@forEach
        }

        if (line.startsWith("- ") || line.startsWith("* ")) {
            flushParagraph()
            flushQuote()
            listItems += line.drop(2)
            return@forEach
        }

        if (line.startsWith("> ")) {
            flushParagraph()
            flushList()
            quote += line.drop(2)
            return@forEach
        }

        flushList()
        flushQuote()
        paragraph += line
    }

    if (inCode) blocks += MarkdownBlock.Code(code.toString(), codeLanguage)
    flushInline()
    return blocks.ifEmpty { listOf(MarkdownBlock.Paragraph(text)) }
}
