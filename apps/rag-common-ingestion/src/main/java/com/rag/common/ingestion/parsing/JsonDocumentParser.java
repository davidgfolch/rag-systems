package com.rag.common.ingestion.parsing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rag.common.core.domain.BlockType;
import com.rag.common.core.domain.Document;
import com.rag.common.core.domain.DocumentBlock;
import com.rag.common.core.domain.ParsedDocument;
import com.rag.common.core.domain.SourceLocation;
import com.rag.common.core.domain.SourceType;
import com.rag.common.core.services.DocumentParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * JSON parser that keeps records and paths instead of flattening the tree.
 * Arrays emit one block per element so sibling records can share a chunk.
 */
public class JsonDocumentParser implements DocumentParser {

    private static final Logger log = LoggerFactory.getLogger(JsonDocumentParser.class);
    private static final String ROOT_PATH = "$";
    private static final String PATH_SEPARATOR = ".";
    private static final String ARRAY_OPEN = "[";
    private static final String ARRAY_CLOSE = "]";

    private final ObjectMapper om = new ObjectMapper();

    @Override
    public ParsedDocument parse(Document document) {
        var root = read(document);
        var blocks = new ArrayList<DocumentBlock>();
        if (root.isArray()) {
            addArray(root, ROOT_PATH, List.of(), blocks);
        } else if (root.isObject()) {
            addObject(root, List.of(), blocks);
        } else {
            blocks.add(block(BlockType.JSON_OBJECT, root.toPrettyString(), List.of(), ROOT_PATH));
        }
        log.debug("Parsed JSON document {} into {} blocks", document.getId(), blocks.size());
        return new ParsedDocument(document.getId(), SourceType.JSON,
                ParsedMetadata.withFormat(document, SourceType.JSON), blocks);
    }

    private JsonNode read(Document document) {
        try {
            var root = om.readTree(document.getContent());
            if (root == null || root.isNull() || root.isMissingNode()) {
                throw new IllegalStateException("Empty JSON document " + document.getId());
            }
            return root;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse JSON document " + document.getId(), e);
        }
    }

    private void addObject(JsonNode node, List<String> section, List<DocumentBlock> blocks) {
        var scalars = om.createObjectNode();
        for (Map.Entry<String, JsonNode> field : node.properties()) {
            var child = field.getValue();
            var childPath = JsonDocumentParser.ROOT_PATH + PATH_SEPARATOR + field.getKey();
            if (child.isArray()) {
                flushScalars(scalars, section, blocks);
                addArray(child, childPath, append(section, field.getKey()), blocks);
            } else if (child.isObject()) {
                flushScalars(scalars, section, blocks);
                blocks.add(block(BlockType.JSON_OBJECT, child.toPrettyString(),
                        append(section, field.getKey()), childPath));
            } else {
                scalars.set(field.getKey(), child);
            }
        }
        flushScalars(scalars, section, blocks);
    }

    private void addArray(JsonNode array, String path, List<String> section, List<DocumentBlock> blocks) {
        for (int index = 0; index < array.size(); index++) {
            var element = array.get(index);
            var childPath = path + ARRAY_OPEN + index + ARRAY_CLOSE;
            blocks.add(block(typeOf(element), element.isValueNode() ? element.asText() : element.toPrettyString(),
                    section, childPath));
        }
    }

    private void flushScalars(ObjectNode scalars, List<String> section, List<DocumentBlock> blocks) {
        if (!scalars.isEmpty()) {
            blocks.add(block(BlockType.JSON_OBJECT, scalars.toPrettyString(), section, JsonDocumentParser.ROOT_PATH));
            scalars.removeAll();
        }
    }

    private static DocumentBlock block(BlockType type, String content, List<String> section, String path) {
        return new DocumentBlock(type, content, section,
                new SourceLocation(null, null, path, null, null), Map.of());
    }

    private static List<String> append(List<String> section, String key) {
        var next = new ArrayList<>(section);
        next.add(key);
        return List.copyOf(next);
    }

    private static BlockType typeOf(JsonNode node) {
        return node.isArray() ? BlockType.JSON_ARRAY : BlockType.JSON_OBJECT;
    }
}
