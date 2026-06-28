package dev.prpilot.webhook.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class HmacSignatureVerifierTest {

    private static final String SECRET = "test-secret";
    private HmacSignatureVerifier verifier;

    @BeforeEach
    void setUp() {
        verifier = new HmacSignatureVerifier(SECRET);
    }

    @Test
    @DisplayName("returns true when signature matches body")
    void validSignaturePasses() throws Exception {
        byte[] body = """
                {"action":"opened"}
                """.getBytes(StandardCharsets.UTF_8);

        String signature = computeSignature(SECRET, body);

        assertThat(verifier.isValid(signature, body)).isTrue();
    }

    @Test
    @DisplayName("returns false when signature is tampered")
    void tamperedSignatureFails() throws Exception {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        String signature = computeSignature(SECRET, body);

        // Flip one character
        String tampered = signature.substring(0, signature.length() - 1) + "0";

        assertThat(verifier.isValid(tampered, body)).isFalse();
    }

    @Test
    @DisplayName("returns false when body is tampered")
    void tamperedBodyFails() throws Exception {
        byte[] originalBody = "{\"action\":\"opened\"}".getBytes(StandardCharsets.UTF_8);
        byte[] modifiedBody = "{\"action\":\"closed\"}".getBytes(StandardCharsets.UTF_8);

        String signature = computeSignature(SECRET, originalBody);

        assertThat(verifier.isValid(signature, modifiedBody)).isFalse();
    }

    @Test
    @DisplayName("returns false when signature header is null")
    void nullSignatureFails() {
        assertThat(verifier.isValid(null, new byte[]{1, 2, 3})).isFalse();
    }

    @Test
    @DisplayName("returns false when signature has no sha256= prefix")
    void missingPrefixFails() {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        // Compute signature but strip the prefix
        try {
            String raw = computeSignature(SECRET, body).substring("sha256=".length());
            assertThat(verifier.isValid(raw, body)).isFalse();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("returns false when wrong secret was used")
    void wrongSecretFails() throws Exception {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        String signatureFromAttacker = computeSignature("attacker-guess", body);

        assertThat(verifier.isValid(signatureFromAttacker, body)).isFalse();
    }

    private static String computeSignature(String secret, byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
    }
}