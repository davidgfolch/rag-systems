package com.rag.common.ingestion.parsing;

import com.rag.common.core.domain.BlockType;
import com.rag.common.core.domain.Document;
import com.rag.common.core.domain.DocumentBlock;
import com.rag.common.core.domain.ParsedDocument;
import com.rag.common.core.domain.SourceLocation;
import com.rag.common.core.domain.SourceType;
import com.rag.common.core.services.DocumentParser;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TableRow;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.BulletList;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.text.TextContentRenderer;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.rag.common.core.domain.MetadataKeys.CODE_LANGUAGE;

/**
 * Markdown parser backed by commonmark (with GFM tables), so headings, lists,
 * fenced code and tables keep their structure instead of being flattened.
 */
public class MarkdownDocumentParser implements DocumentParser {

    private static final Logger log = LoggerFactory.getLogger(MarkdownDocumentParser.class);
    private static final String LINE_SEPARATOR = "\n";
    private static final String TABLE_SEPARATOR = " | ";
    private static final String INFO_SEPARATOR = " ";

    private final Parser parser = Parser.builder()
            .extensions(List.of(TablesExtension.create()))
            .build();
    private final TextContentRenderer renderer = TextContentRenderer.builder().build();

    @Override
    public ParsedDocument parse(Document document) {
        var root = parser.parse(document.getContent());
        var blocks = new ArrayList<DocumentBlock>();
        walk(root, new ArrayList<>(), blocks);
        log.debug("Parsed Markdown document {} into {} blocks", document.getId(), blocks.size());
        return new ParsedDocument(document.getId(), SourceType.MARKDOWN,
                ParsedMetadata.withFormat(document, SourceType.MARKDOWN), blocks);
    }

    private void walk(Node parent, ArrayList<String> section, List<DocumentBlock> blocks) {
        for (var node = parent.getFirstChild(); node != null; node = node.getNext()) {
            handle(node, section, blocks);
        }
    }

    private void handle(Node node, ArrayList<String> section, List<DocumentBlock> blocks) {
        if (node instanceof Heading heading) {
            addHeading(heading, section, blocks);
        } else if (node instanceof FencedCodeBlock code) {
            addBlock(blocks, BlockType.CODE, code.getLiteral(), section, codeMetadata(code));
        } else if (node instanceof IndentedCodeBlock code) {
            addBlock(blocks, BlockType.CODE, code.getLiteral(), section, Map.of());
        } else if (node instanceof BlockQuote) {
            addBlock(blocks, BlockType.QUOTE, renderer.render(node), section, Map.of());
        } else if (node instanceof BulletList || node instanceof OrderedList) {
            addBlock(blocks, BlockType.LIST, listText(node), section, Map.of());
        } else if (node instanceof TableBlock table) {
            addBlock(blocks, BlockType.TABLE, tableText(table), section, Map.of());
        } else if (node instanceof Paragraph) {
            addBlock(blocks, BlockType.PARAGRAPH, renderer.render(node), section, Map.of());
        } else if (node instanceof HtmlBlock html) {
            addBlock(blocks, BlockType.PARAGRAPH, Jsoup.parse(html.getLiteral()).text(), section, Map.of());
        } else if (node.getFirstChild() != null) {
            walk(node, section, blocks);
        }
    }

    private void addHeading(Heading heading, ArrayList<String> section, List<DocumentBlock> blocks) {
        var text = renderer.render(heading).trim();
        if (text.isEmpty()) {
            return;
        }
        updateSection(section, text, heading.getLevel());
        blocks.add(new DocumentBlock(BlockType.HEADING, text, List.copyOf(section),
                SourceLocation.NONE, Map.of()));
    }

    private static void addBlock(List<DocumentBlock> blocks, BlockType type, String text,
                                 List<String> section, Map<String, Object> metadata) {
        var content = text == null ? "" : text.trim();
        if (!content.isEmpty()) {
            blocks.add(new DocumentBlock(type, content, section, SourceLocation.NONE, metadata));
        }
    }

    private static void updateSection(ArrayList<String> section, String text, int level) {
        while (section.size() >= level) {
            section.removeLast();
        }
        section.add(text);
    }

    private static String listText(Node list) {
        var items = new ArrayList<String>();
        for (var node = list.getFirstChild(); node != null; node = node.getNext()) {
            if (node instanceof ListItem) {
                var item = text(node);
                if (!item.isEmpty()) {
                    items.add(item);
                }
            }
        }
        return String.join(LINE_SEPARATOR, items);
    }

    private static String text(Node node) {
        var builder = new StringBuilder();
        appendText(node, builder);
        return builder.toString().trim();
    }

    private static void appendText(Node node, StringBuilder builder) {
        for (var child = node.getFirstChild(); child != null; child = child.getNext()) {
            if (child instanceof org.commonmark.node.Text text) {
                builder.append(text.getLiteral());
            } else if (child instanceof org.commonmark.node.Code code) {
                builder.append(code.getLiteral());
            } else if (child instanceof org.commonmark.node.SoftLineBreak
                    || child instanceof org.commonmark.node.HardLineBreak) {
                builder.append(INFO_SEPARATOR);
            } else if (child instanceof Paragraph || child instanceof ListItem) {
                if (!builder.isEmpty()) {
                    builder.append(LINE_SEPARATOR);
                }
                appendText(child, builder);
            } else {
                appendText(child, builder);
            }
        }
    }

    private String tableText(TableBlock table) {
        var rows = new ArrayList<String>();
        collectRows(table, rows);
        return String.join(LINE_SEPARATOR, rows);
    }

    private void collectRows(Node node, List<String> rows) {
        for (var child = node.getFirstChild(); child != null; child = child.getNext()) {
            if (child instanceof TableRow) {
                rows.add(rowText(child));
            } else {
                collectRows(child, rows);
            }
        }
    }

    private String rowText(Node row) {
        var cells = new ArrayList<String>();
        for (var node = row.getFirstChild(); node != null; node = node.getNext()) {
            cells.add(cellText(node));
        }
        return String.join(TABLE_SEPARATOR, cells);
    }

    private String cellText(Node cell) {
        var parts = new ArrayList<String>();
        for (var node = cell.getFirstChild(); node != null; node = node.getNext()) {
            parts.add(renderer.render(node).trim());
        }
        return String.join(INFO_SEPARATOR, parts).trim();
    }

    private static Map<String, Object> codeMetadata(FencedCodeBlock code) {
        var info = code.getInfo();
        if (info == null || info.isBlank()) {
            return Map.of();
        }
        return Map.of(CODE_LANGUAGE, info.split(INFO_SEPARATOR)[0]);
    }
}
