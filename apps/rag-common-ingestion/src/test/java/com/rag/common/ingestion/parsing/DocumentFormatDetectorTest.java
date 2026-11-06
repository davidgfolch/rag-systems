package com.rag.common.ingestion.parsing;

import com.rag.common.core.domain.Document;
import com.rag.common.core.domain.SourceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;

import static com.rag.common.core.domain.MetadataKeys.CONTENT_TYPE;
import static com.rag.common.core.domain.MetadataKeys.FILE_NAME;
import static com.rag.common.core.domain.MetadataKeys.FORMAT;
import static com.rag.common.core.domain.MetadataKeys.RAW_BYTES;
import static com.rag.common.core.domain.MetadataKeys.SOURCE;
import static org.assertj.core.api.Assertions.assertThat;

class DocumentFormatDetectorTest {

    @Test
    void explicitFormatWinsOverFileName() {
        var document = new Document("d1", "", Map.of(FORMAT, "PDF", FILE_NAME, "notes.txt"));
        assertThat(DocumentFormatDetector.detect(document)).isEqualTo(SourceType.PDF);
    }

    @Test
    void ignoresInvalidExplicitFormatAndFallsBackToExtension() {
        var document = new Document("d1", "", Map.of(FORMAT, "binary", FILE_NAME, "guide.md"));
        assertThat(DocumentFormatDetector.detect(document)).isEqualTo(SourceType.MARKDOWN);
    }

    @ParameterizedTest(name = "file={0} -> {1}")
    @CsvSource({
            "report.pdf, PDF",
            "letter.docx, DOCX",
            "page.html, HTML",
            "page.htm, HTML",
            "readme.md, MARKDOWN",
            "data.json, JSON",
            "notes.txt, TEXT"
    })
    void detectsFormatFromFileName(String fileName, SourceType expected) {
        var document = new Document("d1", "", Map.of(FILE_NAME, fileName));
        assertThat(DocumentFormatDetector.detect(document)).isEqualTo(expected);
    }

    @Test
    void detectsFormatFromSourceUrlIgnoringQueryAndFragment() {
        var document = new Document("d1", "", Map.of(SOURCE, "https://example.com/docs/guide.md?raw=1#top"));
        assertThat(DocumentFormatDetector.detect(document)).isEqualTo(SourceType.MARKDOWN);
    }

    @Test
    void detectsFormatFromContentTypeWithCharset() {
        var document = new Document("d1", "", Map.of(CONTENT_TYPE, "text/html; charset=utf-8"));
        assertThat(DocumentFormatDetector.detect(document)).isEqualTo(SourceType.HTML);
    }

    @Test
    void sniffsPdfMagicBytesWhenNothingElseIdentifiesTheFormat() {
        var document = new Document("d1", "", Map.of(RAW_BYTES, new byte[]{'%', 'P', 'D', 'F', '-', '1'}));
        assertThat(DocumentFormatDetector.detect(document)).isEqualTo(SourceType.PDF);
    }

    @Test
    void sniffsZipMagicBytesAsDocx() {
        var document = new Document("d1", "", Map.of(RAW_BYTES, new byte[]{'P', 'K', 3, 4}));
        assertThat(DocumentFormatDetector.detect(document)).isEqualTo(SourceType.DOCX);
    }

    @Test
    void returnsUnknownWhenNoSignalIsAvailable() {
        var document = new Document("d1", "plain", Map.of());
        assertThat(DocumentFormatDetector.detect(document)).isEqualTo(SourceType.UNKNOWN);
    }
}
