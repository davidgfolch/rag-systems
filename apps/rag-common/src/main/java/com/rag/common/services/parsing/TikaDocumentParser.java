package com.rag.common.services.parsing;

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
 * Tika-based parser for binary formats (PDF, DOCX, XLSX, PPTX, RTF, ...).
 *
 * <p>Uses Apache Tika's automatic content-type detection and extracts textual
 * content from the raw bytes carried in the document metadata.
 */
public class TikaDocumentParser implements DocumentParser {

    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");

    @Override
    public String parse(Document document) {
        Object raw = document.getMetadata().get("raw");
        if (!(raw instanceof String rawContent)) {
            return document.getContent();
        }

        try {
            Metadata metadata = new Metadata();
            ParseContext parseContext = new ParseContext();
            ToXMLContentHandler handler = new ToXMLContentHandler();
            new AutoDetectParser().parse(
                    new ByteArrayInputStream(rawContent.getBytes(StandardCharsets.UTF_8)),
                    handler, metadata, parseContext);
            return HTML_TAG.matcher(handler.toString()).replaceAll(" ").trim();
        } catch (TikaException | SAXException | IOException e) {
            throw new IllegalStateException("Failed to parse document " + document.getId(), e);
        }
    }
}