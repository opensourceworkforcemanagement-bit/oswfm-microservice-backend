package org.oswfm.commons.model.user;

import java.util.Base64;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Represents authentication tokens used for access and refresh.
 * This class contains the access token, its expiration time, and the refresh token.
 */
@Slf4j
@Getter
@Builder
public class Token {

    private String accessToken;
    private Long accessTokenExpiresAt;
    private String refreshToken;

    private static final String TOKEN_PREFIX = "Bearer ";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Checks if the provided authorization header contains a Bearer token.
     *
     * @param authorizationHeader the authorization header to check
     * @return {@code true} if the header contains a Bearer token, {@code false} otherwise
     */
    public static boolean isBearerToken(final String authorizationHeader) {
        return StringUtils.hasText(authorizationHeader) &&
                authorizationHeader.startsWith(TOKEN_PREFIX);
    }

    /**
     * Extracts the JWT from the provided authorization header.
     *
     * @param authorizationHeader the authorization header containing the JWT
     * @return the JWT extracted from the header
     */
    public static String getJwt(final String authorizationHeader) {
        return authorizationHeader.replace(TOKEN_PREFIX, "");
    }

    /**
     * Extracts the {@code userId} claim from an unverified JWT's payload.
     *
     * @param jwt the raw JWT (without the {@code Bearer } prefix)
     * @return the {@code userId} claim, or {@code null} if absent or the token is malformed
     */
    public static String extractUserId(final String jwt) {
        if (!StringUtils.hasText(jwt)) return null;
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length < 2) return null;
            String json = new String(Base64.getUrlDecoder().decode(parts[1]));
            JsonNode claims = OBJECT_MAPPER.readTree(json);
            JsonNode userIdNode = claims.get("userId");
            return userIdNode != null && !userIdNode.isNull() ? userIdNode.asText() : null;
        } catch (Exception e) {
            log.warn("Failed to extract userId from token: {}", e.getMessage());
            return null;
        }
    }

}
