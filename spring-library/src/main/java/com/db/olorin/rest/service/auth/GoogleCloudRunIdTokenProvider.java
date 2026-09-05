package com.db.olorin.rest.service.auth;

import com.db.olorin.rest.exception.AuthServiceException;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.IdTokenCredentials;
import com.google.auth.oauth2.IdTokenProvider;

import java.io.IOException;

public class GoogleCloudRunIdTokenProvider implements CloudRunIdTokenProvider {

    @Override
    public String getIdToken(String audience) {
        try {
            GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();
            if (!(credentials instanceof IdTokenProvider idTokenProvider)) {
                throw new AuthServiceException(
                    "Application default credentials do not support ID token generation");
            }

            IdTokenCredentials tokenCredentials = IdTokenCredentials.newBuilder()
                .setIdTokenProvider(idTokenProvider)
                .setTargetAudience(audience)
                .setOptions(java.util.List.of(IdTokenProvider.Option.FORMAT_FULL))
                .build();
            AccessToken idToken = tokenCredentials.refreshAccessToken();
            if (idToken == null || idToken.getTokenValue() == null || idToken.getTokenValue().isBlank()) {
                throw new AuthServiceException(
                    "Cloud Run ID token provider returned an empty token for audience '%s'".formatted(audience));
            }
            return idToken.getTokenValue();
        } catch (IOException e) {
            throw new AuthServiceException(
                "Failed to generate Cloud Run ID token for audience '%s'".formatted(audience), e);
        }
    }
}
