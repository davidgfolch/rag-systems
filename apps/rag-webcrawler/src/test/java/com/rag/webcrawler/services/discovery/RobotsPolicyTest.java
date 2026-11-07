package com.rag.webcrawler.services.discovery;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RobotsPolicyTest {

    @Test
    void readsSitemapDirectivesForAnyAgent() {
        var robots = RobotsPolicy.parse("""
                User-agent: Googlebot
                Disallow: /private/
                Sitemap: https://example.com/sitemap-a.xml
                Sitemap: https://example.com/sitemap-b.xml
                """);
        assertThat(robots.sitemaps())
                .containsExactly("https://example.com/sitemap-a.xml", "https://example.com/sitemap-b.xml");
        assertThat(robots.allows("/private/page")).isTrue();
    }

    @Test
    void appliesWildcardDisallowRules() {
        var robots = RobotsPolicy.parse("""
                User-agent: *
                Disallow: /private/
                Disallow: /search
                """);
        assertThat(robots.allows("/guide/intro")).isTrue();
        assertThat(robots.allows("/private/page")).isFalse();
        assertThat(robots.allows("/search?q=x")).isFalse();
    }

    @Test
    void ignoresCommentsAndEmptyRules() {
        var robots = RobotsPolicy.parse("""
                # global
                User-agent: *
                Disallow:
                Disallow: /tmp/ # temp
                """);
        assertThat(robots.allows("/anything")).isTrue();
        assertThat(robots.allows("/tmp/file")).isFalse();
    }

    @Test
    void allowsEverythingWhenBlank() {
        assertThat(RobotsPolicy.parse(null).allows("/x")).isTrue();
        assertThat(RobotsPolicy.parse("   ").allows("/x")).isTrue();
        assertThat(RobotsPolicy.parse("").sitemaps()).isEmpty();
    }
}
