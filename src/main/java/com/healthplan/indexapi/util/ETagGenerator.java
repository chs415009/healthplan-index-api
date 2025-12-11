package com.healthplan.indexapi.util;

import lombok.experimental.UtilityClass;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * MD5 hash: JSON -> ETag
 */
@UtilityClass
public class ETagGenerator {

    public static String generate(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hashBytes = md.digest(content.getBytes()); // cannot digest String so via byte[]
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            // Generally, MD5 always exists
            throw new RuntimeException("MD5 algorithm not found", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
