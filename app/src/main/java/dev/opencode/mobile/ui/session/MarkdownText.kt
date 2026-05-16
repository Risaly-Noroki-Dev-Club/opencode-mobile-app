package dev.opencode.mobile.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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
                        1 -> MaterialTheme.typography.titleLarge
                        2 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    },
                    fontWeight = FontWeight.SemiBold,
                )
                is MarkdownBlock.Quote -> Text(
                    text = block.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(12.dp),
                )
                is MarkdownBlock.ListItems -> Column {
                    block.items.forEach { item ->
                        Text(text = "• $item", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                is MarkdownBlock.Paragraph -> Text(text = block.text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun CodeBlock(code: String, language: String?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(14.dp),
    ) {
        if (!language.isNullOrBlank()) {
            Text(
                text = language,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(6.dp))
        }
        Text(
            text = code.trimEnd(),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
        )
    }
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
