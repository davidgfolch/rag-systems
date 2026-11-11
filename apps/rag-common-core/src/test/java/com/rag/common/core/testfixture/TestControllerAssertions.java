package com.rag.common.core.testfixture;

import org.springframework.http.ResponseEntity;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.CREATED;

public final class TestControllerAssertions {

    private TestControllerAssertions() {
    }

    public static <T> void assertEntityCreated(ResponseEntity<T> res, Consumer<T> bodyAssertions) {
        assertThat(res.getStatusCode()).isEqualTo(CREATED);
        bodyAssertions.accept(res.getBody());
    }
}
