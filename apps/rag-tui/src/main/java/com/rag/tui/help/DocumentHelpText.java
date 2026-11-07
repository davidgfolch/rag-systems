package com.rag.tui.help;

import java.util.LinkedHashMap;
import java.util.Map;

/** Detailed help text for document and ingestion commands. */
public final class DocumentHelpText {

    private DocumentHelpText() {
    }

    public static Map<String, String> details() {
        var details = new LinkedHashMap<String, String>();
        details.put("documents", """
                Usage:
                  documents

                Output:
                  Documents of every reachable module, grouped per module, with
                  document id, chunk count, ingestion time and title. Use the id with
                  'delete'.
                """);
        details.put("delete", """
                Usage:
                  delete                 pick a document interactively
                  delete <document-id>   delete by id

                Arguments:
                  <document-id>   id shown by 'documents', e.g. file-<uuid>

                Notes:
                  Searches every reachable module, so the active module does not
                  matter. Deletion happens immediately and is not confirmed; re-ingest
                  the source to restore it.
                """);
        details.put("add-file", """
                Usage:
                  add-file           prompt for the path
                  add-file <path>    ingest one local file

                Arguments:
                  <path>   file path (relative or absolute); PDF, DOCX, TXT and HTML
                           are parsed by Tika when rag.parsing.mode=tika

                Notes:
                  The file is read, submitted and polled in the background, so the
                  shell stays usable while chunks are embedded. Identical content
                  (SHA-256) is detected first and you are asked whether to override;
                  an identical ingestion already in flight is skipped.
                """);
        details.put("add-folder", """
                Usage:
                  add-folder           prompt for the directory
                  add-folder <path>    ingest every supported file below <path>

                Arguments:
                  <path>   directory; its subdirectories are traversed recursively

                Notes:
                  Same parsing and content-hash deduplication as add-file, but
                  duplicates are skipped without asking. Each file is submitted
                  separately and reported as it completes.
                """);
        details.put("add-url", """
                Usage:
                  add-url                          prompt for the URL
                  add-url <url>                    start an interactive breadth-first crawl
                  add-url <url> --max-pages <n>    cap the crawl at <n> pages
                  add-url <url> --all              crawl every discovered page
                  add-url <url> --no-discover      follow only the root page's links

                Arguments:
                  <url>   http(s) URL of the first page

                Options:
                  --max-pages <n>   maximum pages fetched (default 50)
                  --all             no practical page cap (uses the server discovery limit)
                  --no-discover     skip sitemap/navigation discovery; crawl the root
                                    page's outbound links in ranked order

                Notes:
                  By default the module discovers the site's pages (robots.txt sitemap,
                  conventional sitemap locations and the root page navigation), removes
                  duplicate versions/locales and visits the tree section by section, so
                  a capped crawl still covers every chapter. The TUI asks one question
                  per page to steer link ranking (blank = no guidance). After the
                  initial pages one "Go on?" question decides whether to crawl more.
                  Answer 'add-url-cancel' or 'cancel' at any prompt to stop. Pages are
                  ingested asynchronously and de-duplicated by domain + URI.
                """);
        details.put("add-url-cancel", """
                Usage:
                  add-url-cancel

                Notes:
                  Cancels the crawl currently being driven and prints how many pages
                  were submitted, skipped and how many links were filtered out. Safe
                  to run when nothing is crawling ("No URL crawl is active.").
                """);
        return details;
    }
}
