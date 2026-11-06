package com.rag.common.ingestion.parsing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rag.common.core.domain.Document;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.ToXMLContentHandler;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;

import static com.rag.common.core.domain.MetadataKeys.RAW;
import static com.rag.common.core.domain.MetadataKeys.RAW_BYTES;

/** Extracts Tika's XHTML view of a binary document, keeping the original bytes out of the model. */
public class TikaXhtmlExtractor {

    private static final Logger log = LoggerFactory.getLogger(TikaXhtmlExtractor.class);

    public byte[] rawBytes(Document document) {
        var raw = document.getMetadata().get(RAW_BYTES);
        if (raw instanceof byte[] bytes) {
            return bytes;
        }
        var legacy = document.getMetadata().get(RAW);
        if (legacy instanceof String rawContent) {
            return Base64.getDecoder().decode(rawContent);
        }
        return new byte[0];
    }

    public String extract(Document document, byte[] rawBytes) {
        try {
            var handler = new ToXMLContentHandler();
            new AutoDetectParser().parse(new ByteArrayInputStream(rawBytes), handler,
                    new Metadata(), new ParseContext());
            var xhtml = handler.toString();
            log.debug("Extracted {} XHTML characters from document {}", xhtml.length(), document.getId());
            return xhtml;
        } catch (TikaException | SAXException | IOException e) {
            throw new IllegalStateException("Failed to parse document " + document.getId(), e);
        }
    }
}
