package com.rag.tui.help;

import java.util.LinkedHashMap;
import java.util.Map;

/** Detailed help text for chat, provider and pipeline variant commands. */
public final class ChatHelpText {

    private ChatHelpText() {
    }

    public static Map<String, String> details() {
        var details = new LinkedHashMap<String, String>();
        details.put("ask", """
                Usage:
                  ask              prompt for the question
                  ask <question>   ask the active module and stream the answer

                Arguments:
                  <question>   natural-language question; multi-word questions can be
                               typed as-is

                Notes:
                  Retrieves the top-k chunks (rag.chat.top-k, default 4) using the
                  active variant's embeddings, then streams the answer with its source
                  chunks. Each ask starts a new TUI conversation id (tui-<timestamp>);
                  use 'history' to see what rag-memory has recorded.
                """);
        details.put("history", """
                Usage:
                  history

                Output:
                  Every conversation recorded by rag-memory with its id, title and
                  message count.
                """);
        details.put("connect", """
                Usage:
                  connect                          show active models and catalog status
                  connect catalog [<provider>]     browse the catalog, optionally filtered
                  connect chat [<provider>] [<model>]       switch the chat model
                  connect embedding [<provider>] [<model>]  switch the embedding model
                  connect refresh                  re-fetch the model catalog

                Arguments:
                  <provider>   provider id, e.g. ollama or openai
                  <model>      model id printed by 'connect catalog'

                Notes:
                  Missing provider/model open a picker, so bare 'connect chat' works
                  too. The switch is runtime-only (no restart) and drives the sealed
                  'boot' variant; other variants keep the embedding model they were
                  created with. An unknown provider is offered for registration on the
                  spot.
                """);
        details.put("variant", """
                Usage:
                  variant                        list variants and pick one to activate
                  variant <variant-id>           activate a variant by id
                  variant add                    create one interactively
                  variant add <strategy> [<size>] [<overlap>]
                                                 create one; missing values are prompted

                Arguments:
                  <variant-id>   id shown by 'variant', e.g. boot or v2
                  <strategy>     RECURSIVE, FIXED, TOKEN or SEMANTIC
                  <size>         chunk window: characters for RECURSIVE, FIXED and
                                 SEMANTIC, whitespace tokens for TOKEN; must be > 0
                  <overlap>      window repeated between chunks; 0 <= overlap < size

                Recommended values:
                  RECURSIVE 512/128   prose and mixed docs - start here
                  RECURSIVE 384/96    code and API reference pages
                  FIXED     512/128   homogeneous text only (logs, reviews)
                  TOKEN     256/64    strict token windows (size/4 must exceed overlap/4)
                  SEMANTIC  512/128   high-value documents; ~4.5x the embedding cost

                Notes:
                  Blank prompts keep the default shown in brackets, which is the active
                  variant's window (the seeded boot variant uses rag.chunking.size /
                  overlap). A new variant inherits the active embedding model and
                  dimension and stores its vectors in its own variant_<id> table, so
                  switching never mixes vectors and needs no restart. For SEMANTIC the
                  window is an upper bound: topic shifts cut earlier and oversized
                  sentences are hard-split.
                """);
        return details;
    }
}
