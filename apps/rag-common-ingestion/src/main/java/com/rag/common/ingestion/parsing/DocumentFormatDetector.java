package com.rag.common.ingestion.parsing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rag.common.core.domain.Document;
import com.rag.common.core.domain.SourceType;

import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import static com.rag.common.core.domain.MetadataKeys.CONTENT_TYPE;
import static com.rag.common.core.domain.MetadataKeys.FILE_NAME;
import static com.rag.common.core.domain.MetadataKeys.FORMAT;
import static com.rag.common.core.domain.MetadataKeys.RAW_BYTES;
import static com.rag.common.core.domain.MetadataKeys.SOURCE;

/**
 * Resolves a document's format from explicit metadata, then its extension, then
 * its content type, and only as a last resort from binary magic bytes.
 */
public final class DocumentFormatDetector {

    private static final Logger log = LoggerFactory.getLogger(DocumentFormatDetector.class);

    private static final Map<String, SourceType> EXTENSIONS = Map.ofEntries(
            Map.entry("pdf", SourceType.PDF),
            Map.entry("docx", SourceType.DOCX),
            Map.entry("html", SourceType.HTML),
            Map.entry("htm", SourceType.HTML),
            Map.entry("md", SourceType.MARKDOWN),
            Map.entry("markdown", SourceType.MARKDOWN),
            Map.entry("json", SourceType.JSON),
            Map.entry("txt", SourceType.TEXT),
            Map.entry("text", SourceType.TEXT));

    private static final Map<String, SourceType> CONTENT_TYPES = Map.ofEntries(
            Map.entry("application/pdf", SourceType.PDF),
            Map.entry("application/vnd.openxmlformats-officedocument.wordprocessingml.document", SourceType.DOCX),
            Map.entry("text/html", SourceType.HTML),
            Map.entry("application/xhtml+xml", SourceType.HTML),
            Map.entry("text/markdown", SourceType.MARKDOWN),
            Map.entry("application/json", SourceType.JSON),
            Map.entry("text/plain", SourceType.TEXT));

    private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F'};
    private static final byte[] ZIP_MAGIC = {'P', 'K'};
    private static final String CONTENT_TYPE_SEPARATOR = ";";
    private static final String EXTENSION_SEPARATOR = ".";
    private static final String QUERY_SEPARATOR = "?";
    private static final String FRAGMENT_SEPARATOR = "#";

    private DocumentFormatDetector() {
    }

    public static SourceType detect(Document document) {
        var detected = resolve(document.getMetadata(), document);
        log.debug("Detected {} for document {}", detected, document.getId());
        return detected;
    }

    private static SourceType resolve(Map<String, Object> metadata, Document document) {
        var explicit = explicitFormat(metadata.get(FORMAT));
        if (explicit != null) {
            return explicit;
        }
        var extension = fromExtension(metadata);
        if (extension != null) {
            return extension;
        }
        var contentType = fromContentType(metadata.get(CONTENT_TYPE));
        return contentType != null ? contentType : sniff(document);
    }

    private static SourceType explicitFormat(Object value) {
        if (!(value instanceof String text) || text.isBlank()) {
            return null;
        }
        try {
            return SourceType.valueOf(text.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException _) {
            return null;
        }
    }

    private static SourceType fromExtension(Map<String, Object> metadata) {
        var fileName = metadata.get(FILE_NAME);
        var path = fileName instanceof String name && !name.isBlank()
                ? name : pathOf(metadata.get(SOURCE));
        if (path == null || path.isBlank()) {
            return null;
        }
        var extension = afterLastDot(path);
        return extension == null ? null : EXTENSIONS.get(extension);
    }

    private static SourceType fromContentType(Object value) {
        if (!(value instanceof String text) || text.isBlank()) {
            return null;
        }
        var contentType = text.split(CONTENT_TYPE_SEPARATOR)[0].trim().toLowerCase(Locale.ROOT);
        return CONTENT_TYPES.get(contentType);
    }

    private static String pathOf(Object value) {
        if (!(value instanceof String source) || source.isBlank()) {
            return null;
        }
        try {
            return URI.create(source).getPath();
        } catch (IllegalArgumentException _) {
            return source;
        }
    }

    private static String afterLastDot(String path) {
        var clean = path.split(Pattern.quote(QUERY_SEPARATOR))[0].split(Pattern.quote(FRAGMENT_SEPARATOR))[0];
        int dot = clean.lastIndexOf(EXTENSION_SEPARATOR);
        return dot < 0 || dot == clean.length() - 1 ? null : clean.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static SourceType sniff(Document document) {
        var raw = document.getMetadata().get(RAW_BYTES);
        if (!(raw instanceof byte[] bytes)) {
            return SourceType.UNKNOWN;
        }
        if (startsWith(bytes, PDF_MAGIC)) {
            return SourceType.PDF;
        }
        return startsWith(bytes, ZIP_MAGIC) ? SourceType.DOCX : SourceType.UNKNOWN;
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
