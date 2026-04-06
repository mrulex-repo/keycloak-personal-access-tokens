package com.mrulex.keycloak.plugin.pat;

import com.mrulex.keycloak.plugin.pat.dto.PatCredentialData;
import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.Test;
import org.keycloak.credential.CredentialModel;
import org.keycloak.credential.UserCredentialManager;
import org.keycloak.models.UserModel;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Stream;

import org.assertj.core.api.ThrowableAssert;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PatUtilsTest {

    // -------------------------------------------------------------------------
    // generateToken
    // -------------------------------------------------------------------------

    @Test
    void generateToken_is64Chars() {
        assertThat(PatUtils.generateToken()).hasSize(64);
    }

    @Test
    void generateToken_isUrlSafe() {
        assertThat(PatUtils.generateToken()).doesNotContain("+", "/", "=");
    }

    @Test
    void generateToken_isUnique() {
        assertThat(PatUtils.generateToken()).isNotEqualTo(PatUtils.generateToken());
    }

    // -------------------------------------------------------------------------
    // hashToken / verifyToken
    // -------------------------------------------------------------------------

    @Test
    void hashToken_producesArgon2IdHash() {
        assertThat(PatUtils.hashToken("mysecret")).startsWith("$argon2id$");
    }

    @Test
    void verifyToken_returnsTrueForCorrectPlaintext() {
        String plaintext = PatUtils.generateToken();
        String hash = PatUtils.hashToken(plaintext);
        assertThat(PatUtils.verifyToken(plaintext, hash)).isTrue();
    }

    @Test
    void verifyToken_returnsFalseForWrongPlaintext() {
        assertThat(PatUtils.verifyToken("wrong", PatUtils.hashToken("correct"))).isFalse();
    }

    // -------------------------------------------------------------------------
    // buildCredential / extractHash / extractCredentialData
    // -------------------------------------------------------------------------

    @Test
    void buildCredential_setsTypeAndLabel() {
        CredentialModel cred = PatUtils.buildCredential("my-token", "hash123", List.of("role-a"), null);
        assertThat(cred.getType()).isEqualTo(PatUtils.CREDENTIAL_TYPE);
        assertThat(cred.getUserLabel()).isEqualTo("my-token");
    }

    @Test
    void buildCredential_noExpires_omitsField() {
        CredentialModel cred = PatUtils.buildCredential("t", "h", List.of("r"), null);
        assertThat(cred.getCredentialData()).doesNotContain("expires");
    }

    @Test
    void buildCredential_withExpires_includesField() {
        String expires = "2030-01-01T00:00:00Z";
        CredentialModel cred = PatUtils.buildCredential("t", "h", List.of("r"), expires);
        assertThat(cred.getCredentialData()).contains(expires);
    }

    @Test
    void extractHash_roundtrip() {
        CredentialModel cred = PatUtils.buildCredential("t", "myhash", List.of("r"), null);
        assertThat(PatUtils.extractHash(cred)).isEqualTo("myhash");
    }

    @Test
    void extractCredentialData_roundtrip() {
        List<String> roles = List.of("maven-read", "maven-deploy");
        String expires = "2030-06-01T00:00:00Z";
        CredentialModel cred = PatUtils.buildCredential("t", "h", roles, expires);

        PatCredentialData data = PatUtils.extractCredentialData(cred);
        assertThat(data.roles()).containsExactlyInAnyOrderElementsOf(roles);
        assertThat(data.expires()).isEqualTo(expires);
    }

    @Test
    void extractCredentialData_noExpires_returnsNull() {
        CredentialModel cred = PatUtils.buildCredential("t", "h", List.of("r"), null);
        assertThat(PatUtils.extractCredentialData(cred).expires()).isNull();
    }

    // -------------------------------------------------------------------------
    // isExpired
    // -------------------------------------------------------------------------

    @Test
    void isExpired_nullReturnsFalse() {
        assertThat(PatUtils.isExpired(null)).isFalse();
    }

    @Test
    void isExpired_blankReturnsFalse() {
        assertThat(PatUtils.isExpired("")).isFalse();
    }

    @Test
    void isExpired_futureReturnsFalse() {
        assertThat(PatUtils.isExpired(Instant.now().plus(1, ChronoUnit.DAYS).toString())).isFalse();
    }

    @Test
    void isExpired_pastReturnsTrue() {
        assertThat(PatUtils.isExpired(Instant.now().minus(1, ChronoUnit.DAYS).toString())).isTrue();
    }

    @Test
    void isExpired_malformedReturnsFalse() {
        assertThat(PatUtils.isExpired("not-a-date")).isFalse();
    }

    // -------------------------------------------------------------------------
    // validateTokenName
    // -------------------------------------------------------------------------

    @Test
    void validateTokenName_validName_noException() {
        assertThatNoException().isThrownBy(() -> PatUtils.validateTokenName("maven-ci", userWithPats()));
    }

    @Test
    void validateTokenName_nullName_throws400() {
        assertBadRequest(() -> PatUtils.validateTokenName(null, userWithPats()));
    }

    @Test
    void validateTokenName_startsWithDigit_throws400() {
        assertBadRequest(() -> PatUtils.validateTokenName("1token", userWithPats()));
    }

    @Test
    void validateTokenName_uppercase_throws400() {
        assertBadRequest(() -> PatUtils.validateTokenName("MyToken", userWithPats()));
    }

    @Test
    void validateTokenName_maxLength_noException() {
        // exactly 64 chars: 1 letter + 63 alphanumeric — should pass
        assertThatNoException().isThrownBy(() ->
                PatUtils.validateTokenName("a" + "x".repeat(63), userWithPats()));
    }

    @Test
    void validateTokenName_tooLong_throws400() {
        // 65 chars — one over the limit
        assertBadRequest(() -> PatUtils.validateTokenName("a" + "x".repeat(64), userWithPats()));
    }

    @Test
    void validateTokenName_duplicateName_throws400() {
        CredentialModel existing = PatUtils.buildCredential("maven-ci", "h", List.of("r"), null);
        assertBadRequest(() -> PatUtils.validateTokenName("maven-ci", userWithPats(existing)));
    }

    // -------------------------------------------------------------------------
    // validateExpires
    // -------------------------------------------------------------------------

    @Test
    void validateExpires_null_noException() {
        assertThatNoException().isThrownBy(() -> PatUtils.validateExpires(null));
    }

    @Test
    void validateExpires_blank_noException() {
        assertThatNoException().isThrownBy(() -> PatUtils.validateExpires(""));
    }

    @Test
    void validateExpires_futureDate_noException() {
        String future = Instant.now().plus(30, ChronoUnit.DAYS).toString();
        assertThatNoException().isThrownBy(() -> PatUtils.validateExpires(future));
    }

    @Test
    void validateExpires_pastDate_throws400() {
        assertBadRequest(() -> PatUtils.validateExpires(Instant.now().minus(1, ChronoUnit.DAYS).toString()));
    }

    @Test
    void validateExpires_invalidFormat_throws400() {
        assertBadRequest(() -> PatUtils.validateExpires("2030-13-45"));
    }

    // -------------------------------------------------------------------------
    // validateRoles
    // -------------------------------------------------------------------------

    @Test
    void validateRoles_nonEmptyList_noException() {
        assertThatNoException().isThrownBy(() -> PatUtils.validateRoles(List.of("maven-read")));
    }

    @Test
    void validateRoles_null_throws400() {
        assertBadRequest(() -> PatUtils.validateRoles(null));
    }

    @Test
    void validateRoles_empty_throws400() {
        assertBadRequest(() -> PatUtils.validateRoles(List.of()));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void assertBadRequest(ThrowableAssert.ThrowingCallable callable) {
        assertThatExceptionOfType(WebApplicationException.class)
                .isThrownBy(callable)
                .satisfies(e -> assertThat(e.getResponse().getStatus()).isEqualTo(400));
    }

    private UserModel userWithPats(CredentialModel... credentials) {
        UserCredentialManager credMgr = mock(UserCredentialManager.class);
        when(credMgr.getStoredCredentialsByTypeStream(PatUtils.CREDENTIAL_TYPE))
                .thenAnswer(inv -> Stream.of(credentials));

        UserModel user = mock(UserModel.class);
        when(user.credentialManager()).thenReturn(credMgr);
        return user;
    }
}
