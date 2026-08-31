package com.rag.tui;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the rag-tui application. Launches an interactive terminal for
 * ingesting documents/webpages and chatting with the RAG system.
 */
@SpringBootApplication
public class RagTuiApplication {

    public static void main(String[] args) {
        SpringApplication.run(RagTuiApplication.class, args);
    }
}
