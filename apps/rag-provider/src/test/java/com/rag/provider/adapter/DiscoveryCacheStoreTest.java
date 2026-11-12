package com.rag.provider.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.provider.domain.DiscoveryCacheSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class DiscoveryCacheStoreTest {

    private static final ObjectMapper OM = new ObjectMapper();
    private static final String KIND = "ollama";
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @TempDir Path directory;

    private MutableClock clock;
    private DiscoveryCacheSettings settings;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(NOW);
        settings = new DiscoveryCacheSettings(directory.resolve("cache.json"),
                Duration.ofSeconds(3600), Duration.ofSeconds(60));
    }

    @Test
    void shouldReturnEmptyWhenCacheFileIsMissing() {
        assertThat(store().read(KIND)).isEmpty();
    }

    @Test
    void shouldReadBackWrittenPositiveEntry() {
        var sut = store();
        sut.write(KIND, "192.168.1.10");
        assertThat(sut.read(KIND)).hasValueSatisfying(entry -> {
            assertThat(entry.host()).isEqualTo("192.168.1.10");
            assertThat(entry.checkedAtEpochSeconds()).isEqualTo(NOW.getEpochSecond());
        });
    }

    @Test
    void shouldExpirePositiveEntryAfterPositiveTtl() {
        store().write(KIND, "192.168.1.10");
        clock.advance(Duration.ofSeconds(3600));
        assertThat(store().read(KIND)).isPresent();
        clock.advance(Duration.ofSeconds(1));
        assertThat(store().read(KIND)).isEmpty();
    }

    @Test
    void shouldExpireNegativeEntryAfterNegativeTtl() {
        store().write(KIND, null);
        clock.advance(Duration.ofSeconds(60));
        assertThat(store().read(KIND)).hasValueSatisfying(entry -> assertThat(entry.host()).isNull());
        clock.advance(Duration.ofSeconds(1));
        assertThat(store().read(KIND)).isEmpty();
    }

    @Test
    void shouldReplaceAnExistingEntryAtomically() {
        var sut = store();
        sut.write(KIND, "192.168.1.10");
        sut.write(KIND, "192.168.1.20");
        assertThat(sut.read(KIND).orElseThrow().host()).isEqualTo("192.168.1.20");
        assertThat(directory.resolve("cache.json")).exists();
    }

    @Test
    void shouldIgnoreMalformedJson() throws IOException {
        Files.writeString(settings.file(), "{not-json");
        var sut = store();
        assertThat(sut.read(KIND)).isEmpty();
        sut.write(KIND, "192.168.1.10");
        assertThat(sut.read(KIND).orElseThrow().host()).isEqualTo("192.168.1.10");
    }

    @Test
    void shouldIgnoreNonObjectRootAndEntriesPayloads() throws IOException {
        Files.writeString(settings.file(), "[1,2]");
        assertThat(store().read(KIND)).isEmpty();
        Files.writeString(settings.file(), "{\"version\":1,\"entries\":[]}");
        assertThat(store().read(KIND)).isEmpty();
        var sut = store();
        sut.write(KIND, "192.168.1.10");
        assertThat(sut.read(KIND).orElseThrow().host()).isEqualTo("192.168.1.10");
    }

    @Test
    void shouldClearEntryAndIgnoreMissingKind() {
        var sut = store();
        sut.write(KIND, "192.168.1.10");
        sut.clear(KIND);
        assertThat(sut.read(KIND)).isEmpty();
        sut.clear(KIND);
    }

    @Test
    void shouldIgnoreDirectoryInPlaceOfCacheFile() {
        var sut = new DiscoveryCacheStore(OM, new DiscoveryCacheSettings(directory,
                Duration.ofSeconds(3600), Duration.ofSeconds(60)), clock);
        assertThat(sut.read(KIND)).isEmpty();
    }

    @Test
    void shouldNotThrowWhenCacheDirectoryCannotBeCreated() throws IOException {
        var blocker = Files.writeString(directory.resolve("blocker"), "x");
        var sut = new DiscoveryCacheStore(OM, new DiscoveryCacheSettings(blocker.resolve("cache.json"),
                Duration.ofSeconds(3600), Duration.ofSeconds(60)), clock);
        sut.write(KIND, "192.168.1.10");
        assertThat(blocker).isRegularFile();
    }

    private DiscoveryCacheStore store() {
        return new DiscoveryCacheStore(OM, settings, clock);
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public Instant instant() {
            return instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
