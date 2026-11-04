package com.rag.provider.services;

import com.rag.provider.domain.OllamaDiscoverySettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OllamaEndpointResolverTest {

    private static final String LOOPBACK = "http://127.0.0.1:11434";

    @Mock private OllamaDiscoveryService discovery;
    private OllamaEndpointResolver sut;

    @BeforeEach
    void setUp() {
        sut = new OllamaEndpointResolver(settings(11434), discovery);
    }

    @ParameterizedTest(name = "should resolve local spec \"{0}\" without discovery")
    @ValueSource(strings = {"local", "localhost", "127.0.0.1", "LOCAL", "  Local  "})
    void shouldResolveLocalSpecToLoopback(String spec) {
        assertThat(sut.resolve(spec)).isEqualTo(LOOPBACK);
        verifyNoInteractions(discovery);
    }

    @ParameterizedTest(name = "should resolve auto spec \"{0}\" through discovery")
    @ValueSource(strings = {"auto", "discover", "AUTO", "  discover  "})
    void shouldResolveAutoSpecThroughDiscovery(String spec) {
        when(discovery.discover()).thenReturn(Optional.of("192.168.1.50"));
        assertThat(sut.resolve(spec)).isEqualTo("http://192.168.1.50:11434");
    }

    @ParameterizedTest(name = "should treat blank spec \"{0}\" as auto")
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void shouldTreatBlankSpecAsAuto(String spec) {
        when(discovery.discover()).thenReturn(Optional.of("10.0.0.5"));
        assertThat(sut.resolve(spec)).isEqualTo("http://10.0.0.5:11434");
    }

    @Test
    void shouldFallBackToLoopbackWhenDiscoveryFindsNothing() {
        when(discovery.discover()).thenReturn(Optional.empty());
        assertThat(sut.resolve("auto")).isEqualTo(LOOPBACK);
    }

    @Test
    void shouldHonourTheConfiguredDiscoveryPort() {
        sut = new OllamaEndpointResolver(settings(11500), discovery);
        when(discovery.discover()).thenReturn(Optional.of("10.0.0.5"));
        assertThat(sut.resolve("discover")).isEqualTo("http://10.0.0.5:11500");
    }

    @ParameterizedTest(name = "should keep explicit spec \"{0}\" as \"{1}\"")
    @CsvSource({
            "http://10.0.0.5:9999, http://10.0.0.5:9999",
            "https://api.example.com/v1, https://api.example.com/v1",
            "http://host/, http://host",
            "http://host///, http://host",
            "10.0.0.5, http://10.0.0.5:11434",
            "10.0.0.5:9999, http://10.0.0.5:9999",
            "ollama-box, http://ollama-box:11434"
    })
    void shouldNormalizeExplicitSpecs(String spec, String expected) {
        assertThat(sut.resolve(spec)).isEqualTo(expected);
        verifyNoInteractions(discovery);
    }

    private static OllamaDiscoverySettings settings(int port) {
        return new OllamaDiscoverySettings(true, port, Duration.ofMillis(100),
                Duration.ofMillis(500), 1024);
    }
}
