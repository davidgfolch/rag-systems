package com.rag.webcrawler.services.fetching;

import com.rag.contract.model.PageBlockDTO;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Walks a fetched page and emits ordered structural blocks, so the ingestion
 * pipeline receives the page's heading hierarchy instead of a flat string.
 * The crawler stays free of parsing/embedding dependencies; it only describes
 * what jsoup already sees.
 */
final class PageBlockExtractor {

    static final String TITLE = "TITLE";
    static final String HEADING = "HEADING";
    static final String PARAGRAPH = "PARAGRAPH";
    static final String LIST = "LIST";
    static final String TABLE = "TABLE";
    static final String CODE = "CODE";
    static final String QUOTE = "QUOTE";

    private static final Set<String> SKIPPED = Set.of(
            "script", "style", "noscript", "head", "template", "nav", "aside", "footer");
    private static final Set<String> HEADINGS = Set.of("h1", "h2", "h3", "h4", "h5", "h6");
    private static final Set<String> CONTAINERS = Set.of("html", "body", "main", "article", "section",
            "div", "header", "footer", "figure", "figcaption", "details", "summary");
    private static final Set<String> LISTS = Set.of("ul", "ol");
    private static final String TAG_PRE = "pre";
    private static final String TAG_TABLE = "table";
    private static final String TAG_QUOTE = "blockquote";
    private static final String TAG_PARAGRAPH = "p";
    private static final String TAG_ROW = "tr";
    private static final String CELL_SEPARATOR = " | ";
    private static final String LINE_SEPARATOR = "\n";
    private static final String LIST_ITEM_SELECTOR = "> li";

    List<PageBlockDTO> extract(org.jsoup.nodes.Document document) {
        var blocks = new ArrayList<PageBlockDTO>();
        addTitle(document.title(), blocks);
        var root = document.body() != null ? document.body() : document;
        walk(root, new ArrayList<>(), blocks);
        return blocks;
    }

    private void walk(Element parent, ArrayList<String> section, List<PageBlockDTO> blocks) {
        for (var child : parent.children()) {
            var tag = child.normalName();
            if (SKIPPED.contains(tag)) {
                continue;
            }
            if (HEADINGS.contains(tag)) {
                addHeading(child, tag, section, blocks);
            } else if (TAG_PRE.equals(tag)) {
                add(blocks, CODE, child.text(), section, child);
            } else if (TAG_TABLE.equals(tag)) {
                add(blocks, TABLE, tableText(child), section, child);
            } else if (LISTS.contains(tag)) {
                add(blocks, LIST, listText(child), section, child);
            } else if (TAG_QUOTE.equals(tag)) {
                add(blocks, QUOTE, child.text(), section, child);
            } else if (TAG_PARAGRAPH.equals(tag)) {
                add(blocks, PARAGRAPH, child.text(), section, child);
            } else if (hasBlockChildren(child)) {
                walk(child, new ArrayList<>(section), blocks);
            } else {
                add(blocks, PARAGRAPH, child.text(), section, child);
            }
        }
    }

    private static void addTitle(String title, List<PageBlockDTO> blocks) {
        if (title != null && !title.isBlank()) {
            blocks.add(new PageBlockDTO(TITLE, title.trim()));
        }
    }

    private static void addHeading(Element element, String tag, ArrayList<String> section,
                                   List<PageBlockDTO> blocks) {
        var text = element.text().trim();
        if (text.isEmpty()) {
            return;
        }
        updateSection(section, text, Integer.parseInt(tag.substring(1)));
        blocks.add(block(HEADING, text, section, element));
    }

    private static void add(List<PageBlockDTO> blocks, String type, String text,
                            List<String> section, Element element) {
        if (text != null && !text.isBlank()) {
            blocks.add(block(type, text.trim(), section, element));
        }
    }

    private static PageBlockDTO block(String type, String text, List<String> section, Element element) {
        return new PageBlockDTO(type, text)
                .sectionPath(List.copyOf(section))
                .domPath(safeSelector(element));
    }

    private static void updateSection(ArrayList<String> section, String text, int level) {
        while (section.size() >= level) {
            section.removeLast();
        }
        section.add(text);
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

    private static String listText(Element list) {
        return String.join(LINE_SEPARATOR, list.select(LIST_ITEM_SELECTOR).eachText()).trim();
    }

    private static String tableText(Element table) {
        var rows = new ArrayList<String>();
        for (var row : table.select(TAG_ROW)) {
            var cells = row.children().stream().map(Element::text).map(String::trim).toList();
            rows.add(String.join(CELL_SEPARATOR, cells));
        }
        return String.join(LINE_SEPARATOR, rows);
    }

    private static String safeSelector(Element element) {
        try {
            return element.cssSelector();
        } catch (RuntimeException _) {
            return null;
        }
    }
}
