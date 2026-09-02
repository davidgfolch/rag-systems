package com.rag.tui;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * Entry point for the rag-tui application. Launches an interactive terminal for
 * ingesting documents/webpages and chatting with the RAG system. Runs as a
 * non-web CLI ({@code web-application-type: none}); the test-only {@code support}
 * stub apps must never be picked up by this scan.
 */
@SpringBootApplication
@ComponentScan(excludeFilters = @ComponentScan.Filter(
        type = FilterType.REGEX, pattern = "com\\.rag\\.tui\\.support\\..*"))
public class RagTuiApplication {

    public static void main(String[] args) {
        SpringApplication.run(RagTuiApplication.class, args);
    }
}
