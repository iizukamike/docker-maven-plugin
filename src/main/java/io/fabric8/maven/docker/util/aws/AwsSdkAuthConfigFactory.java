package io.fabric8.maven.docker.util.aws;

import io.fabric8.maven.docker.access.AuthConfig;
import io.fabric8.maven.docker.util.Logger;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.net.URLEncoder;

import static java.nio.charset.StandardCharsets.UTF_8;

public class AwsSdkAuthConfigFactory {

    private final Logger log;

    public AwsSdkAuthConfigFactory(Logger log) {
        this.log = log;
    }

    public AuthConfig createAuthConfig() {
        // v2 auth
        try {
            Class<?> credentialsProviderChainClass = Class.forName("software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider");
            Object credentialsProviderChain = credentialsProviderChainClass.getMethod("create").invoke(null);
            Object credentials = credentialsProviderChainClass.getMethod("resolveCredentials").invoke(credentialsProviderChain);
            if (credentials != null) {
                Class<?> sessionCredentialsClass = Class.forName("software.amazon.awssdk.auth.credentials.AwsSessionCredentials");
                String sessionToken = sessionCredentialsClass.isInstance(credentials)
                        ? (String) sessionCredentialsClass.getMethod("sessionToken").invoke(credentials) : null;

                Class<?> credentialsClass = Class.forName("software.amazon.awssdk.auth.credentials.AwsCredentials");
                return new AuthConfig(
                        (String) credentialsClass.getMethod("accessKeyId").invoke(credentials),
                        (String) credentialsClass.getMethod("secretAccessKey").invoke(credentials),
                        "none",
                        sessionToken
                );
            }
        } catch (ClassNotFoundException e) {
            log.warn("Not using AWS v2 API, sdk not found");
        } catch (InvocationTargetException e) {
            log.warn("Unable to load credentials via AWS v2 API");
            log.debug(e.getCause().getMessage());
        } catch (IllegalAccessException | NoSuchMethodException e) {
            log.warn("Unable to load credentials via AWS v2 API");
            log.debug(e.getMessage());
        }

        try {
            Class<?> credentialsProviderChainClass = Class.forName("com.amazonaws.auth.DefaultAWSCredentialsProviderChain");
            Object credentialsProviderChain = credentialsProviderChainClass.getDeclaredConstructor().newInstance();
            Object credentials = credentialsProviderChainClass.getMethod("getCredentials").invoke(credentialsProviderChain);
            if (credentials == null) {
                return null;
            }

            Class<?> sessionCredentialsClass = Class.forName("com.amazonaws.auth.AWSSessionCredentials");
            String sessionToken = sessionCredentialsClass.isInstance(credentials)
                    ? (String) sessionCredentialsClass.getMethod("getSessionToken").invoke(credentials) : null;

            Class<?> credentialsClass = Class.forName("com.amazonaws.auth.AWSCredentials");
            return new AuthConfig(
                    (String) credentialsClass.getMethod("getAWSAccessKeyId").invoke(credentials),
                    (String) credentialsClass.getMethod("getAWSSecretKey").invoke(credentials),
                    "none",
                    sessionToken
            );
        } catch (Throwable t) {
            String issueTitle = null;
            try {
                issueTitle = URLEncoder.encode("Failed calling AWS SDK: " + t.getMessage(), UTF_8.name());
            } catch (UnsupportedEncodingException ignore) {
            }
            log.warn("Failed to fetch AWS credentials: %s", t.getMessage());
            if (t.getCause() != null) {
                log.warn("Caused by: %s", t.getCause().getMessage());
            }
            log.warn("Please report a bug at https://github.com/fabric8io/docker-maven-plugin/issues/new?%s",
                    issueTitle == null ? "" : "title=?" + issueTitle);
            log.warn("%s", t);
            return null;
        }
    }

}
