package com.rag.common.testfixture;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

public final class TestControllerAssertions {

    private TestControllerAssertions() {
    }

    public static <T> void assertEntityCreated(ResponseEntity<T> response, Consumer<T> bodyAssertions) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        bodyAssertions.accept(response.getBody());
    }
}
