package com.rag.common.core.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MetadataKeysTest {

    @Test
    void exposesContentHashKeyForCrossModuleDedup() {
        assertThat(MetadataKeys.CONTENT_HASH).isEqualTo("contentHash");
    }
}
