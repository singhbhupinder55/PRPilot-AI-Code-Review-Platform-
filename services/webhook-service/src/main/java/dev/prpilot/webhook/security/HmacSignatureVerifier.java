package dev.prpilot.webhook.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Verifies the X-Hub-Signature-256 header sent by GitHub.
 *
 * GitHub computes HMAC-SHA256 of the raw request body using a shared secret,
 * and sends it as "sha256=<hex>". We recompute and compare in constant time.
 *
 * Docs: https://docs.github.com/webhooks/using-webhooks/validating-webhook-deliveries
 */
@Component
public class HmacSignatureVerifier {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String SIGNATURE_PREFIX = "sha256=";

    private final byte[] secretBytes;

    public HmacSignatureVerifier(
            @Value("${prpilot.github.webhook-secret}") String secret) {
        this.secretBytes = secret.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Returns true if the signature matches the body.
     * Constant-time comparison prevents timing attacks.
     */
    public boolean isValid(String signatureHeader, byte[] rawBody) {
        if (signatureHeader == null || !signatureHeader.startsWith(SIGNATURE_PREFIX)) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secretBytes, HMAC_ALGORITHM));
            byte[] expected = mac.doFinal(rawBody);
            String expectedHex = SIGNATURE_PREFIX + HexFormat.of().formatHex(expected);
            return MessageDigest.isEqual(
                    expectedHex.getBytes(StandardCharsets.UTF_8),
                    signatureHeader.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            return false;
        }
    }
}