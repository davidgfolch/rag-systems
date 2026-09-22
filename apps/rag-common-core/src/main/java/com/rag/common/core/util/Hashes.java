package com.rag.common.core.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Message digest helpers shared across modules. Kept stateless so callers can
 * compute a canonical SHA-256 over raw bytes without duplicating digest setup.
 */
public final class Hashes {

    private static final String SHA_256 = "SHA-256";

    private Hashes() {
    }

    public static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(SHA_256).digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest not available on this JVM", e);
        }
    }
}