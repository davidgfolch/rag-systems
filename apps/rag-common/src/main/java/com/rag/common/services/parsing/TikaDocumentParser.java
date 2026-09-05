package com.rag.common.services.parsing;

import com.rag.common.domain.Document;
import com.rag.common.services.DocumentParser;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.ToXMLContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.regex.Pattern;

/**
 * Tika-based parser for binary formats (PDF, DOCX, XLSX, PPTX, RTF, ...).
 *
 * <p>Uses Apache Tika's automatic content-type detection and extracts textual
 * content from the document's raw bytes, carried either as a {@code byte[]}
 * ({@code rawBytes} metadata, from multipart upload) or as a base64 string
 * ({@code raw} metadata) for backward compatibility.
 */
public class TikaDocumentParser implements DocumentParser {

    private static final Logger log = LoggerFactory.getLogger(TikaDocumentParser.class);

    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");

    @Override
    public String parse(Document document) {
        byte[] rawBytes = getRawBytes(document);
        if (rawBytes.length == 0) {
            return document.getContent();
        }
        try {
            String parsed = HTML_TAG.matcher(parseText(rawBytes)).replaceAll(" ").trim();
            log.debug("Parsed document {} ({} raw bytes -> {} chars)", document.getId(), rawBytes.length, parsed.length());
            return parsed;
        } catch (TikaException | SAXException | IOException e) {
            throw new IllegalStateException("Failed to parse document " + document.getId(), e);
        }
    }

    private byte[] getRawBytes(Document document) {
        Object raw = document.getMetadata().get("rawBytes");
        if (raw instanceof byte[] bytes) {
            return bytes;
        }
        Object legacy = document.getMetadata().get("raw");
        if (legacy instanceof String rawContent) {
            return Base64.getDecoder().decode(rawContent);
        }
        return new byte[0];
    }

    private String parseText(byte[] rawBytes) throws SAXException, IOException, TikaException {
        Metadata metadata = new Metadata();
        ParseContext parseContext = new ParseContext();
        ToXMLContentHandler handler = new ToXMLContentHandler();
        new AutoDetectParser().parse(new ByteArrayInputStream(rawBytes), handler, metadata, parseContext);
        return handler.toString();
    }
}