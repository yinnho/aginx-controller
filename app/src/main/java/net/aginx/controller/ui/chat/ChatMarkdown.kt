package net.aginx.controller.ui.chat

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import org.commonmark.Extension
import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableBody
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableHead
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.task.list.items.TaskListItemMarker
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Document
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.Heading
import org.commonmark.node.HardLineBreak
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text as MarkdownTextNode
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.Parser
import java.util.Locale

private const val LIST_INDENT_DP = 14

private val markdownParser: Parser by lazy {
    val extensions: List<Extension> = listOf(
        AutolinkExtension.create(),
        StrikethroughExtension.create(),
        TablesExtension.create(),
        TaskListItemsExtension.create(),
    )
    Parser.builder()
        .extensions(extensions)
        .build()
}

@Composable
fun ChatMarkdown(text: String, textColor: Color) {
    val document = remember(text) { markdownParser.parse(text) as Document }
    val codeBg = MaterialTheme.colorScheme.surfaceVariant
    val codeColor = MaterialTheme.colorScheme.onSurfaceVariant
    val linkColor = MaterialTheme.colorScheme.primary

    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            RenderMarkdownBlocks(
                start = document.firstChild,
                textColor = textColor,
                listDepth = 0,
                codeBg = codeBg,
                codeColor = codeColor,
                linkColor = linkColor,
            )
        }
    }
}

@Composable
private fun RenderMarkdownBlocks(
    start: Node?,
    textColor: Color,
    listDepth: Int,
    codeBg: Color,
    codeColor: Color,
    linkColor: Color,
) {
    var node = start
    while (node != null) {
        val current = node
        when (current) {
            is Paragraph -> {
                RenderParagraph(
                    paragraph = current,
                    textColor = textColor,
                    codeBg = codeBg,
                    codeColor = codeColor,
                    linkColor = linkColor,
                )
            }
            is Heading -> {
                val headingText = remember(current) {
                    buildInlineMarkdown(current.firstChild, codeBg, codeColor, linkColor)
                }
                Text(
                    text = headingText,
                    style = headingStyle(current.level),
                    color = textColor,
                )
            }
            is FencedCodeBlock -> {
                ChatCodeBlock(
                    code = current.literal.orEmpty(),
                    language = current.info?.trim()?.ifEmpty { null }
                )
            }
            is IndentedCodeBlock -> {
                ChatCodeBlock(code = current.literal.orEmpty(), language = null)
            }
            is BlockQuote -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)),
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RenderMarkdownBlocks(
                            start = current.firstChild,
                            textColor = textColor,
                            listDepth = listDepth,
                            codeBg = codeBg,
                            codeColor = codeColor,
                            linkColor = linkColor,
                        )
                    }
                }
            }
            is BulletList -> {
                RenderBulletList(
                    list = current,
                    textColor = textColor,
                    listDepth = listDepth,
                    codeBg = codeBg,
                    codeColor = codeColor,
                    linkColor = linkColor,
                )
            }
            is OrderedList -> {
                RenderOrderedList(
                    list = current,
                    textColor = textColor,
                    listDepth = listDepth,
                    codeBg = codeBg,
                    codeColor = codeColor,
                    linkColor = linkColor,
                )
            }
            is TableBlock -> {
                RenderTableBlock(
                    table = current,
                    textColor = textColor,
                    codeBg = codeBg,
                    codeColor = codeColor,
                    linkColor = linkColor,
                )
            }
            is ThematicBreak -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)),
                )
            }
            is HtmlBlock -> {
                val literal = current.literal.orEmpty().trim()
                if (literal.isNotEmpty()) {
                    Text(
                        text = literal,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = textColor,
                    )
                }
            }
        }
        node = current.next
    }
}

@Composable
private fun RenderParagraph(
    paragraph: Paragraph,
    textColor: Color,
    codeBg: Color,
    codeColor: Color,
    linkColor: Color,
) {
    val annotated = remember(paragraph) {
        buildInlineMarkdown(paragraph.firstChild, codeBg, codeColor, linkColor)
    }
    if (annotated.text.trimEnd().isEmpty()) {
        return
    }

    val context = LocalContext.current
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    Text(
        text = annotated,
        style = MaterialTheme.typography.bodyMedium,
        color = textColor,
        onTextLayout = { layoutResult = it },
        modifier = Modifier.pointerInput(annotated) {
            detectTapGestures { offset ->
                layoutResult?.let { layout ->
                    val position = layout.getOffsetForPosition(offset)
                    annotated.getStringAnnotations(position, position)
                        .firstOrNull()
                        ?.let { annotation ->
                            if (annotation.tag == "URL") {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(annotation.item))
                                context.startActivity(intent)
                            }
                        }
                }
            }
        }
    )
}

@Composable
private fun RenderBulletList(
    list: BulletList,
    textColor: Color,
    listDepth: Int,
    codeBg: Color,
    codeColor: Color,
    linkColor: Color,
) {
    Column(
        modifier = Modifier.padding(start = (LIST_INDENT_DP * listDepth).dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        var item = list.firstChild
        while (item != null) {
            if (item is ListItem) {
                RenderListItem(
                    item = item,
                    markerText = "•",
                    textColor = textColor,
                    listDepth = listDepth,
                    codeBg = codeBg,
                    codeColor = codeColor,
                    linkColor = linkColor,
                )
            }
            item = item.next
        }
    }
}

@Composable
private fun RenderOrderedList(
    list: OrderedList,
    textColor: Color,
    listDepth: Int,
    codeBg: Color,
    codeColor: Color,
    linkColor: Color,
) {
    Column(
        modifier = Modifier.padding(start = (LIST_INDENT_DP * listDepth).dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        var index = list.markerStartNumber ?: 1
        var item = list.firstChild
        while (item != null) {
            if (item is ListItem) {
                RenderListItem(
                    item = item,
                    markerText = "$index.",
                    textColor = textColor,
                    listDepth = listDepth,
                    codeBg = codeBg,
                    codeColor = codeColor,
                    linkColor = linkColor,
                )
                index += 1
            }
            item = item.next
        }
    }
}

@Composable
private fun RenderListItem(
    item: ListItem,
    markerText: String,
    textColor: Color,
    listDepth: Int,
    codeBg: Color,
    codeColor: Color,
    linkColor: Color,
) {
    var contentStart: Node? = item.firstChild
    var marker = markerText
    val task = contentStart as? TaskListItemMarker
    if (task != null) {
        marker = if (task.isChecked) "☑" else "☐"
        contentStart = task.next
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = marker,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = textColor,
            modifier = Modifier.width(24.dp),
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            RenderMarkdownBlocks(
                start = contentStart,
                textColor = textColor,
                listDepth = listDepth + 1,
                codeBg = codeBg,
                codeColor = codeColor,
                linkColor = linkColor,
            )
        }
    }
}

@Composable
private fun RenderTableBlock(
    table: TableBlock,
    textColor: Color,
    codeBg: Color,
    codeColor: Color,
    linkColor: Color,
) {
    val rows = remember(table) { buildTableRows(table, codeBg, codeColor, linkColor) }
    if (rows.isEmpty()) return

    val maxCols = rows.maxOf { row -> row.cells.size }.coerceAtLeast(1)
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)),
    ) {
        for (row in rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (index in 0 until maxCols) {
                    val cell = row.cells.getOrNull(index) ?: AnnotatedString("")
                    Text(
                        text = cell,
                        style = if (row.isHeader)
                            MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
                        else
                            MaterialTheme.typography.bodySmall,
                        color = textColor,
                        modifier = Modifier
                            .border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f))
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .width(160.dp),
                    )
                }
            }
        }
    }
}

private fun buildTableRows(
    table: TableBlock,
    codeBg: Color,
    codeColor: Color,
    linkColor: Color,
): List<TableRenderRow> {
    val rows = mutableListOf<TableRenderRow>()
    var child: Node? = table.firstChild
    while (child != null) {
        when (child) {
            is TableHead -> rows.addAll(readTableSection(child, isHeader = true, codeBg, codeColor, linkColor))
            is TableBody -> rows.addAll(readTableSection(child, isHeader = false, codeBg, codeColor, linkColor))
            is TableRow -> rows.add(readTableRow(child, isHeader = false, codeBg, codeColor, linkColor))
        }
        child = child.next
    }
    return rows
}

private fun readTableSection(
    section: Node,
    isHeader: Boolean,
    codeBg: Color,
    codeColor: Color,
    linkColor: Color,
): List<TableRenderRow> {
    val rows = mutableListOf<TableRenderRow>()
    var row: Node? = section.firstChild
    while (row != null) {
        if (row is TableRow) {
            rows.add(readTableRow(row, isHeader = isHeader, codeBg, codeColor, linkColor))
        }
        row = row.next
    }
    return rows
}

private fun readTableRow(
    row: TableRow,
    isHeader: Boolean,
    codeBg: Color,
    codeColor: Color,
    linkColor: Color,
): TableRenderRow {
    val cells = mutableListOf<AnnotatedString>()
    var cellNode: Node? = row.firstChild
    while (cellNode != null) {
        if (cellNode is TableCell) {
            cells.add(buildInlineMarkdown(cellNode.firstChild, codeBg, codeColor, linkColor))
        }
        cellNode = cellNode.next
    }
    return TableRenderRow(isHeader = isHeader, cells = cells)
}

private fun buildInlineMarkdown(
    start: Node?,
    codeBg: Color,
    codeColor: Color,
    linkColor: Color,
): AnnotatedString {
    return buildAnnotatedString {
        appendInlineNode(start, codeBg, codeColor, linkColor)
    }
}

private fun AnnotatedString.Builder.appendInlineNode(
    node: Node?,
    codeBg: Color,
    codeColor: Color,
    linkColor: Color,
) {
    var current: Node? = node
    while (current != null) {
        val n = current
        when (n) {
            is MarkdownTextNode -> append(n.literal)
            is SoftLineBreak -> append('\n')
            is HardLineBreak -> append('\n')
            is Code -> {
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = codeBg,
                        color = codeColor,
                    ),
                ) {
                    append(n.literal)
                }
            }
            is Emphasis -> {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    appendInlineNode(n.firstChild, codeBg, codeColor, linkColor)
                }
            }
            is StrongEmphasis -> {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                    appendInlineNode(n.firstChild, codeBg, codeColor, linkColor)
                }
            }
            is Strikethrough -> {
                withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                    appendInlineNode(n.firstChild, codeBg, codeColor, linkColor)
                }
            }
            is Link -> {
                val url = n.destination ?: ""
                pushStringAnnotation(tag = "URL", annotation = url)
                withStyle(
                    SpanStyle(
                        color = linkColor,
                        textDecoration = TextDecoration.Underline,
                    ),
                ) {
                    appendInlineNode(n.firstChild, codeBg, codeColor, linkColor)
                }
                pop()
            }
            is HtmlInline -> {
                if (!n.literal.isNullOrBlank()) {
                    append(n.literal)
                }
            }
            else -> {
                appendInlineNode(n.firstChild, codeBg, codeColor, linkColor)
            }
        }
        current = n.next
    }
}

@Composable
private fun headingStyle(level: Int): TextStyle {
    return when (level.coerceIn(1, 6)) {
        1 -> MaterialTheme.typography.titleLarge
        2 -> MaterialTheme.typography.titleMedium
        3 -> MaterialTheme.typography.titleSmall
        4 -> MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
        else -> MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
    }
}

private data class TableRenderRow(
    val isHeader: Boolean,
    val cells: List<AnnotatedString>,
)

@Composable
fun ChatCodeBlock(code: String, language: String?) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (!language.isNullOrBlank()) {
                Text(
                    text = language.uppercase(Locale.US),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = code.trimEnd(),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
