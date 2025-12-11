package com.healthplan.indexapi.security;

import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.SignedJWT;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.util.Date;
import java.util.List;

/**
 * Role: Responsible for performing the cryptographic and claims validation
 * of a JWT (JSON Web Token), typically an external ID token (e.g., from Google).
 */
@Slf4j
@Component
public class TokenValidator {

    private static final List<String> ALLOWED_ISSUERS = List.of(
            "https://accounts.google.com",
            "accounts.google.com"
    );

    private static final String GOOGLE_JWKS_URI = "https://www.googleapis.com/oauth2/v3/certs";

    /**
     * Validates the given JWT string by issuer, expiration time, digital signature using the corresponding public key.
     */
    public boolean validateToken(String tokenString) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(tokenString);

            String issuer = signedJWT.getJWTClaimsSet().getIssuer();
            Date expirationTime = signedJWT.getJWTClaimsSet().getExpirationTime();
            String keyId = signedJWT.getHeader().getKeyID();

            log.info("Validating token - Issuer: {}, KeyID: {}", issuer, keyId);

            if (!ALLOWED_ISSUERS.contains(issuer)) {
                log.warn("Untrusted issuer: {}", issuer);
                return false;
            }

            if (expirationTime.before(new Date())) {
                log.warn("Token expired at: {}", expirationTime);
                return false;
            }

            JWK publicKey = getPublicKey(keyId);
            RSAKey rsaKey = (RSAKey) publicKey;
            JWSVerifier verifier = new RSASSAVerifier(rsaKey);

            if (!signedJWT.verify(verifier)) {
                log.warn("Signature verification failed");
                return false;
            }

            log.info("Token validation successful");
            return true;

        } catch (Exception e) {
            log.error("Token validation error: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Fetches provided Key ID of the public JWK (JSON Web Key) from the Google JWKS URI
     */
    private JWK getPublicKey(String keyId) throws Exception {
        JWKSet jwkSet = JWKSet.load(new URL(GOOGLE_JWKS_URI));
        JWK key = jwkSet.getKeyByKeyId(keyId);

        if (key == null) {
            throw new IllegalArgumentException("Public key not found for kid: " + keyId);
        }

        return key;
    }
}
