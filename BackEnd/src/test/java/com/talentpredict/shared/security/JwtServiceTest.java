package com.talentpredict.shared.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests unitaires pour {@link JwtService}.
 * <p>
 * Aucun contexte Spring : les propriétés {@code @Value} sont injectées
 * via {@link ReflectionTestUtils}.
 */
@DisplayName("JwtService — Tests Unitaires")
class JwtServiceTest {

    private static final String TEST_SECRET =
        "TestSecretKeyForJWTAuthenticationMustBeLongEnough256Bits"; // 56 chars ≥ 32 bytes

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secret", TEST_SECRET);
        ReflectionTestUtils.setField(jwtService, "accessTokenExpiration",  43_200_000L); // 12 h
        ReflectionTestUtils.setField(jwtService, "refreshTokenExpiration", 604_800_000L); // 7 j
    }

    /* ==========================================================
     *  Génération de tokens
     * ========================================================== */

    @Nested
    @DisplayName("Génération")
    class GenerationTests {

        @Test
        @DisplayName("✅ generateAccessToken : retourne un JWT non vide")
        void shouldGenerateNonEmptyAccessToken() {
            String token = jwtService.generateAccessToken("alice@test.com");

            assertThat(token).isNotBlank().contains(".");
        }

        @Test
        @DisplayName("✅ generateRefreshToken : retourne un JWT non vide")
        void shouldGenerateNonEmptyRefreshToken() {
            String token = jwtService.generateRefreshToken("alice@test.com");

            assertThat(token).isNotBlank().contains(".");
        }

        @Test
        @DisplayName("✅ generateToken : alias de generateAccessToken")
        void generateTokenShouldBehaveAsAccessToken() {
            String token1 = jwtService.generateToken("user@test.com");
            String token2 = jwtService.generateAccessToken("user@test.com");

            // Les deux doivent extraire le même username
            assertThat(jwtService.extractUsername(token1)).isEqualTo("user@test.com");
            assertThat(jwtService.extractUsername(token2)).isEqualTo("user@test.com");
        }

        @Test
        @DisplayName("✅ Access token contient le claim type=access")
        void accessTokenShouldHaveCorrectType() {
            String token = jwtService.generateAccessToken("user@test.com");

            assertThat(jwtService.extractTokenType(token)).isEqualTo("access");
        }

        @Test
        @DisplayName("✅ Refresh token contient le claim type=refresh")
        void refreshTokenShouldHaveCorrectType() {
            String token = jwtService.generateRefreshToken("user@test.com");

            assertThat(jwtService.extractTokenType(token)).isEqualTo("refresh");
        }
    }

    /* ==========================================================
     *  Extraction de claims
     * ========================================================== */

    @Nested
    @DisplayName("Extraction")
    class ExtractionTests {

        @Test
        @DisplayName("✅ extractUsername : retourne le bon username")
        void shouldExtractUsername() {
            String token = jwtService.generateAccessToken("bob@test.com");

            assertThat(jwtService.extractUsername(token)).isEqualTo("bob@test.com");
        }

        @Test
        @DisplayName("✅ extractExpiration : retourne une date dans le futur pour un token frais")
        void shouldExtractFutureExpiration() {
            String token = jwtService.generateAccessToken("carol@test.com");

            assertThat(jwtService.extractExpiration(token))
                .isAfter(new java.util.Date());
        }
    }

    /* ==========================================================
     *  Validation de tokens
     * ========================================================== */

    @Nested
    @DisplayName("Validation")
    class ValidationTests {

        @Test
        @DisplayName("✅ validateToken : valide pour un token frais avec le bon username")
        void shouldValidateCorrectToken() {
            String token = jwtService.generateAccessToken("dave@test.com");

            assertThat(jwtService.validateToken(token, "dave@test.com")).isTrue();
        }

        @Test
        @DisplayName("❌ validateToken : invalide si l'username ne correspond pas")
        void shouldRejectWrongUsername() {
            String token = jwtService.generateAccessToken("dave@test.com");

            assertThat(jwtService.validateToken(token, "eve@test.com")).isFalse();
        }

        @Test
        @DisplayName("❌ validateToken : retourne false pour un token corrompu")
        void shouldReturnFalseForCorruptToken() {
            assertThat(jwtService.validateToken("this.is.not.a.jwt", "user@test.com")).isFalse();
        }

        @Test
        @DisplayName("❌ validateToken : invalide pour un token expiré")
        void shouldRejectExpiredToken() {
            // Configurer une expiration de -1 ms (déjà expiré)
            ReflectionTestUtils.setField(jwtService, "accessTokenExpiration", -1L);
            String expiredToken = jwtService.generateAccessToken("frank@test.com");

            assertThat(jwtService.validateToken(expiredToken, "frank@test.com")).isFalse();
        }
    }

    /* ==========================================================
     *  Hachage de tokens
     * ========================================================== */

    @Nested
    @DisplayName("Hachage (hashToken)")
    class HashTests {

        @Test
        @DisplayName("✅ hashToken : retourne un hash non vide")
        void shouldReturnNonEmptyHash() {
            String hash = jwtService.hashToken("some.jwt.token");

            assertThat(hash).isNotBlank();
        }

        @Test
        @DisplayName("✅ hashToken : déterministe — même entrée → même hash")
        void shouldBeDeterministic() {
            String hash1 = jwtService.hashToken("same.token");
            String hash2 = jwtService.hashToken("same.token");

            assertThat(hash1).isEqualTo(hash2);
        }

        @Test
        @DisplayName("✅ hashToken : des entrées différentes produisent des hashes différents")
        void shouldProduceDifferentHashesForDifferentInputs() {
            String hash1 = jwtService.hashToken("token.A");
            String hash2 = jwtService.hashToken("token.B");

            assertThat(hash1).isNotEqualTo(hash2);
        }

        @Test
        @DisplayName("✅ hashToken : retourne un hash en Base64 valide")
        void shouldReturnBase64EncodedHash() {
            String hash = jwtService.hashToken("any.token");

            // Un hash Base64 standard ne contient que des chars alphanums + / + = + +
            assertThat(hash).matches("^[A-Za-z0-9+/=]+$");
        }
    }
}
