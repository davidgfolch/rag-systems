package com.rag.common.ingestion.parsing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rag.common.core.domain.BlockType;
import com.rag.common.core.domain.DocumentBlock;
import com.rag.common.core.domain.SourceLocation;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.rag.common.core.domain.MetadataKeys.CODE_LANGUAGE;
import static com.rag.common.core.domain.MetadataKeys.TABLE_HEADER;

/**
 * Turns an XHTML tree (from jsoup or Tika) into ordered blocks, preserving the
 * heading hierarchy, page markers and per-block provenance.
 */
public class XhtmlBlockExtractor {

    private static final Logger log = LoggerFactory.getLogger(XhtmlBlockExtractor.class);

    private static final Set<String> SKIPPED = Set.of("script", "style", "noscript", "head", "template", "nav");
    private static final Set<String> HEADINGS = Set.of("h1", "h2", "h3", "h4", "h5", "h6");
    private static final Set<String> CONTAINERS = Set.of("html", "body", "main", "article", "section",
            "div", "header", "footer", "figure", "figcaption", "details", "summary");
    private static final Set<String> LISTS = Set.of("ul", "ol");
    private static final String PAGE_TAG = "div";
    private static final String PAGE_CLASS = "page";
    private static final String TAG_PRE = "pre";
    private static final String TAG_TABLE = "table";
    private static final String TAG_QUOTE = "blockquote";
    private static final String TAG_PARAGRAPH = "p";
    private static final String TAG_ROW = "tr";
    private static final String CELL_SEPARATOR = " | ";
    private static final String LINE_SEPARATOR = "\n";
    private static final String CODE_LANGUAGE_PREFIX = "language-";
    private static final String CODE_LANGUAGE_ATTRIBUTE = "data-language";
    private static final String TERMINAL_PUNCTUATION = ".,;:?!";
    private static final int MAX_HEADING_CHARS = 80;
    private static final int MAX_HEADING_WORDS = 12;

    private final boolean heuristicHeadings;

    public XhtmlBlockExtractor(boolean heuristicHeadings) {
        this.heuristicHeadings = heuristicHeadings;
    }

    public List<DocumentBlock> extract(Document document) {
        var root = document.body() != null ? document.body() : document;
        var blocks = new ArrayList<DocumentBlock>();
        walk(root, new ArrayList<>(), null, blocks);
        if (blocks.isEmpty() && !root.text().isBlank()) {
            blocks.add(block(BlockType.PARAGRAPH, root.text(), List.of(), location(root, null), Map.of()));
        }
        log.debug("Extracted {} blocks from XHTML tree", blocks.size());
        return blocks;
    }

    private void walk(Element parent, ArrayList<String> section, Integer page, List<DocumentBlock> blocks) {
        Integer currentPage = page;
        for (var child : parent.children()) {
            var tag = child.normalName();
            if (SKIPPED.contains(tag)) {
                continue;
            }
            if (isPage(child)) {
                currentPage = nextPage(currentPage);
                walk(child, new ArrayList<>(section), currentPage, blocks);
            } else if (HEADINGS.contains(tag)) {
                addHeading(child, tag, section, currentPage, blocks);
            } else if (TAG_PRE.equals(tag)) {
                addBlock(blocks, BlockType.CODE, child.text(), section, currentPage, child, codeMetadata(child));
            } else if (TAG_TABLE.equals(tag)) {
                addBlock(blocks, BlockType.TABLE, tableText(child), section, currentPage, child, tableMetadata(child));
            } else if (LISTS.contains(tag)) {
                addBlock(blocks, BlockType.LIST, listText(child), section, currentPage, child, Map.of());
            } else if (TAG_QUOTE.equals(tag)) {
                addBlock(blocks, BlockType.QUOTE, child.text(), section, currentPage, child, Map.of());
            } else if (TAG_PARAGRAPH.equals(tag)) {
                addParagraph(child, section, currentPage, blocks);
            } else if (hasBlockChildren(child)) {
                walk(child, new ArrayList<>(section), currentPage, blocks);
            } else if (!child.text().isBlank()) {
                addParagraph(child, section, currentPage, blocks);
            }
        }
    }

    private void addHeading(Element element, String tag, ArrayList<String> section, Integer page,
                            List<DocumentBlock> blocks) {
        var text = element.text().trim();
        if (text.isEmpty()) {
            return;
        }
        updateSection(section, text, headingLevel(tag));
        blocks.add(block(BlockType.HEADING, text, List.copyOf(section), location(element, page), Map.of()));
    }

    private void addParagraph(Element element, ArrayList<String> section, Integer page,
                              List<DocumentBlock> blocks) {
        var text = element.text().trim();
        if (text.isEmpty()) {
            return;
        }
        if (heuristicHeadings && looksLikeHeading(text)) {
            updateSection(section, text, 1);
            blocks.add(block(BlockType.HEADING, text, List.copyOf(section), location(element, page), Map.of()));
            return;
        }
        addBlock(blocks, BlockType.PARAGRAPH, text, section, page, element, Map.of());
    }

    private static void addBlock(List<DocumentBlock> blocks, BlockType type, String text,
                                 List<String> section, Integer page, Element element,
                                 Map<String, Object> metadata) {
        if (!text.isBlank()) {
            blocks.add(block(type, text, List.copyOf(section), location(element, page), metadata));
        }
    }

    private static DocumentBlock block(BlockType type, String text, List<String> section,
                                       SourceLocation location, Map<String, Object> metadata) {
        return new DocumentBlock(type, text, section, location, metadata);
    }

    private static void updateSection(ArrayList<String> section, String text, int level) {
        while (section.size() >= level) {
            section.removeLast();
        }
        section.add(text);
    }

    private static int headingLevel(String tag) {
        return Integer.parseInt(tag.substring(1));
    }

    private static boolean looksLikeHeading(String text) {
        if (text.length() > MAX_HEADING_CHARS) {
            return false;
        }
        if (text.split("\\s+").length > MAX_HEADING_WORDS) {
            return false;
        }
        return TERMINAL_PUNCTUATION.indexOf(text.charAt(text.length() - 1)) < 0;
    }

    private static boolean hasBlockChildren(Element element) {
        return element.children().stream().anyMatch(child -> {
            var tag = child.normalName();
            return HEADINGS.contains(tag) || CONTAINERS.contains(tag) || isBlockElement(tag);
        });
    }

    private static boolean isBlockElement(String tag) {
        return TAG_PRE.equals(tag) || TAG_TABLE.equals(tag) || LISTS.contains(tag)
                || TAG_QUOTE.equals(tag) || TAG_PARAGRAPH.equals(tag);
    }

    private static boolean isPage(Element element) {
        return PAGE_TAG.equals(element.normalName()) && element.hasClass(PAGE_CLASS);
    }

    private static Integer nextPage(Integer page) {
        return page == null ? 1 : page + 1;
    }

    private static String listText(Element element) {
        return String.join(LINE_SEPARATOR, element.select("> li").eachText()).trim();
    }

    private static String tableText(Element table) {
        var rows = new ArrayList<String>();
        for (var row : table.select(TAG_ROW)) {
            var cells = row.children().stream().map(Element::text).map(String::trim).toList();
            rows.add(String.join(CELL_SEPARATOR, cells));
        }
        return String.join(LINE_SEPARATOR, rows).trim();
    }

    private static Map<String, Object> tableMetadata(Element table) {
        var rows = table.select(TAG_ROW);
        if (rows.isEmpty() || rows.getFirst().select("th").isEmpty()) {
            return Map.of();
        }
        var header = rows.getFirst().children().stream().map(Element::text).map(String::trim).toList();
        return Map.of(TABLE_HEADER, String.join(CELL_SEPARATOR, header));
    }

    private static Map<String, Object> codeMetadata(Element element) {
        var language = element.attr(CODE_LANGUAGE_ATTRIBUTE);
        if (language.isBlank()) {
            language = element.classNames().stream()
                    .filter(name -> name.startsWith(CODE_LANGUAGE_PREFIX))
                    .map(name -> name.substring(CODE_LANGUAGE_PREFIX.length()))
                    .findFirst().orElse("");
        }
        return language.isBlank() ? Map.of() : Map.of(CODE_LANGUAGE, language);
    }

    private static SourceLocation location(Element element, Integer page) {
        return new SourceLocation(page, safeSelector(element), null, null, null);
    }

    private static String safeSelector(Element element) {
        try {
            return element.cssSelector();
        } catch (RuntimeException _) {
            return null;
        }
    }
}
