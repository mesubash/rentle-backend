package com.rentle.shared.security;

import com.rentle.config.JwtProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Loads the RS256 keypair from configuration (PEM), or generates an
 * ephemeral pair when none is configured. Ephemeral keys invalidate all
 * tokens on restart — acceptable in dev only.
 */
@Slf4j
@Component
public class JwtKeyProvider {

    private final RSAPrivateKey privateKey;
    private final RSAPublicKey publicKey;

    public JwtKeyProvider(JwtProperties props) {
        try {
            if (props.privateKey() != null && !props.privateKey().isBlank()
                    && props.publicKey() != null && !props.publicKey().isBlank()) {
                KeyFactory kf = KeyFactory.getInstance("RSA");
                this.privateKey = (RSAPrivateKey) kf.generatePrivate(
                        new PKCS8EncodedKeySpec(pemToDer(props.privateKey())));
                this.publicKey = (RSAPublicKey) kf.generatePublic(
                        new X509EncodedKeySpec(pemToDer(props.publicKey())));
            } else {
                log.warn("No JWT keys configured — generating ephemeral RSA keypair (dev only). "
                        + "All tokens are invalidated on restart.");
                KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
                gen.initialize(2048);
                KeyPair pair = gen.generateKeyPair();
                this.privateKey = (RSAPrivateKey) pair.getPrivate();
                this.publicKey = (RSAPublicKey) pair.getPublic();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialise JWT keys", e);
        }
    }

    private static byte[] pemToDer(String pem) {
        String body = pem
                .replaceAll("-----BEGIN [A-Z ]+-----", "")
                .replaceAll("-----END [A-Z ]+-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(body);
    }

    public RSAPrivateKey privateKey() {
        return privateKey;
    }

    public RSAPublicKey publicKey() {
        return publicKey;
    }
}
