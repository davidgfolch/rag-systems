package com.rag.common.testfixture;

import java.nio.charset.StandardCharsets;

public final class TestPdfBytes {

    public static final byte[] MINIMAL = "%PDF-1.0\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj 2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj 3 0 obj<</Type/Page/MediaBox[0 0 3 3]>>endobj\nxref\n0 4\n0000000000 65535 f \n0000000010 00000 n \n0000000059 00000 n \n0000000115 00000 n \ntrailer<</Size 4/Root 1 0 R>>\nstartxref\n190\n%%EOF".getBytes(StandardCharsets.UTF_8);

    private TestPdfBytes() {
    }
}
