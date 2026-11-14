package com.expensetracker.util;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.OptionalInt;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

// Minimal HS256 JWT: header.payload.signature, signed with HMAC-SHA256.
// Only tokens with exactly our header are accepted, so "alg: none" tricks don't work.
public final class JwtUtil {
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private static final String HEADER =
            ENCODER.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final byte[] secret;
    private final long lifetimeSeconds;

    public JwtUtil(String secret, long lifetimeSeconds) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("jwt.secret must be at least 32 characters");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.lifetimeSeconds = lifetimeSeconds;
    }

    public String create(int userId) {
        long now = Instant.now().getEpochSecond();
        String payload = ENCODER.encodeToString(
                ("{\"sub\":\"" + userId + "\",\"iat\":" + now + ",\"exp\":" + (now + lifetimeSeconds) + "}")
                        .getBytes(StandardCharsets.UTF_8));
        String signingInput = HEADER + "." + payload;
        return signingInput + "." + ENCODER.encodeToString(hmac(signingInput));
    }

    // The user id inside a valid, unexpired token; empty for anything else
    public OptionalInt verify(String token) {
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 3 || !HEADER.equals(parts[0])) {
                return OptionalInt.empty();
            }
            byte[] expected = hmac(parts[0] + "." + parts[1]);
            byte[] actual = DECODER.decode(parts[2]);
            if (!MessageDigest.isEqual(expected, actual)) {   // constant-time comparison
                return OptionalInt.empty();
            }
            JsonNode payload = MAPPER.readTree(DECODER.decode(parts[1]));
            if (payload.path("exp").asLong(0) <= Instant.now().getEpochSecond()) {
                return OptionalInt.empty();
            }
            return OptionalInt.of(Integer.parseInt(payload.path("sub").asText()));
        } catch (Exception e) {
            return OptionalInt.empty();   // malformed token
        }
    }

    private byte[] hmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Token signing failed", e);
        }
    }
}