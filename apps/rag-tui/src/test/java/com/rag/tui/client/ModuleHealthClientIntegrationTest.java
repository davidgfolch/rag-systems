package com.rag.tui.client;

import com.rag.tui.support.StubModuleServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for {@link ModuleHealthClient}: with a real Actuator-style
 * health endpoint the module is reported ready; against a dead port it is not.
 */
class ModuleHealthClientIntegrationTest {

    private StubModuleServer stub;
    private ModuleHealthClient sut;

    @BeforeEach
    void setUp() throws IOException {
        stub = new StubModuleServer();
        sut = new ModuleHealthClient(RestClient.builder());
    }

    @AfterEach
    void tearDown() {
        stub.stop();
    }

    @Test
    void detectsHealthyModule() {
        assertThat(sut.isUp(stub.baseUrl())).isTrue();
    }

    @Test
    void waitsUntilModuleIsHealthy() {
        assertThat(sut.waitUntilUp(stub.baseUrl(), 5_000, null)).isTrue();
    }

    @Test
    void reportsDeadModuleAsNotUp() {
        assertThat(sut.isUp("http://localhost:1")).isFalse();
        assertThat(sut.waitUntilUp("http://localhost:1", 100, null)).isFalse();
    }
}