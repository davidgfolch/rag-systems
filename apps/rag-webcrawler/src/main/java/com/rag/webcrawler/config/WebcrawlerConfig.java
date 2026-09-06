package com.rag.webcrawler.config;

import com.rag.common.services.ChatModelPort;
import com.rag.webcrawler.services.WebCrawlService;
import com.rag.webcrawler.services.fetching.JsoupWebPageFetcher;
import com.rag.webcrawler.services.fetching.WebPageFetcher;
import com.rag.webcrawler.services.ranking.DeterministicLinkPrioritizer;
import com.rag.webcrawler.services.ranking.LinkPrioritizer;
import com.rag.webcrawler.services.ranking.LlmLinkPrioritizer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class WebcrawlerConfig {

    @Bean
    public WebPageFetcher webPageFetcher() {
        return new JsoupWebPageFetcher();
    }

    @Bean
    public LinkPrioritizer deterministicLinkPrioritizer() {
        return new DeterministicLinkPrioritizer();
    }

    @Bean
    @Primary
    public LinkPrioritizer linkPrioritizer(
            LinkPrioritizer deterministicLinkPrioritizer,
            ObjectProvider<ChatModelPort> chatModelProvider,
            @Value("${rag.crawler.prioritizer:deterministic}") String mode) {
        if ("llm".equalsIgnoreCase(mode)) {
            var chatModel = chatModelProvider.getIfAvailable();
            if (chatModel != null) return new LlmLinkPrioritizer(chatModel, deterministicLinkPrioritizer);
        }
        return deterministicLinkPrioritizer;
    }

    @Bean
    public WebCrawlService webCrawlService(WebPageFetcher fetcher, LinkPrioritizer linkPrioritizer) {
        return new WebCrawlService(fetcher, linkPrioritizer);
    }
}