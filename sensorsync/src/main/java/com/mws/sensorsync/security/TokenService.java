package com.mws.sensorsync.security;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Tokens de dispositivo: "ss_" + 24 bytes aleatorios em base64url (32 caracteres).
 * Como o token tem alta entropia, SHA-256 (sem sal) e suficiente para guardar no banco
 * e permite buscar o dispositivo direto pelo hash.
 */
@Service
public class TokenService {

    public static final String PREFIX = "ss_";
    private final SecureRandom random = new SecureRandom();

    public String newToken() {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hash(String token) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(token.trim().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public String displayPrefix(String token) {
        return token.substring(0, Math.min(token.length(), 9));
    }
}
