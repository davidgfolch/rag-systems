package com.rag.provider.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.provider.domain.ProviderProfile;
import com.rag.provider.domain.ProviderType;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderProfileFileStoreTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void roundTripsProfilesWithSecrets() throws Exception {
        var file = Files.createTempDirectory("profiles").resolve("profiles.json");
        var sut = new ProviderProfileFileStore(mapper, file);
        var profiles = List.of(
                new ProviderProfile("glhf", ProviderType.OPENAI_COMPATIBLE, "GLHF",
                        "https://glhf.chat", "secret-key"),
                new ProviderProfile("openrouter", ProviderType.OPENAI_COMPATIBLE,
                        "OpenRouter", "https://openrouter.ai/api/v1", "sk-or-2"));
        sut.save(profiles);
        assertThat(sut.load()).usingRecursiveFieldByFieldElementComparator().isEqualTo(profiles);
        assertThat(Files.readString(file)).contains("secret-key");
    }

    @Test
    void loadsEmptyWhenFileMissing() throws Exception {
        var file = Files.createTempDirectory("profiles").resolve("missing.json");
        var sut = new ProviderProfileFileStore(mapper, file);
        assertThat(sut.load()).isEmpty();
    }

    @Test
    void loadsEmptyOnCorruptJson() throws Exception {
        var file = Files.createTempDirectory("profiles").resolve("corrupt.json");
        Files.writeString(file, "{not json");
        var sut = new ProviderProfileFileStore(mapper, file);
        assertThat(sut.load()).isEmpty();
    }
}