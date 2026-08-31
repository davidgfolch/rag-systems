package com.rag.tui.parsing;

import com.rag.common.domain.Document;
import com.rag.common.services.DocumentParser;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.ToXMLContentHandler;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Tika-based parser for binary formats (PDF, DOCX, XLSX, HTML, ...). Uses Apache
 * Tika's automatic content-type detection on the raw bytes carried in metadata.
 */
public class TikaDocumentParser implements DocumentParser {

    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");

    @Override
    public String parse(Document document) {
        Object raw = document.getMetadata().get("raw");
        if (!(raw instanceof String rawContent)) return document.getContent();

        try {
            Metadata metadata = new Metadata();
            ToXMLContentHandler handler = new ToXMLContentHandler();
            new AutoDetectParser().parse(
                    new ByteArrayInputStream(rawContent.getBytes(StandardCharsets.UTF_8)),
                    handler, metadata, new ParseContext());
            return HTML_TAG.matcher(handler.toString()).replaceAll(" ").trim();
        } catch (TikaException | SAXException | IOException e) {
            throw new IllegalStateException("Failed to parse document " + document.getId(), e);
        }
    }
}
