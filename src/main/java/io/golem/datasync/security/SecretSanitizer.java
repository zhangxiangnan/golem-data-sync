package io.golem.datasync.security;

import java.util.Collection;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class SecretSanitizer {
    public String sanitize(String message, Collection<String> secrets) {
        String result = StringUtils.hasText(message) ? message : "Unknown error";
        if (secrets != null) {
            for (String secret : secrets) {
                if (StringUtils.hasText(secret)) {
                    result = result.replace(secret, "******");
                }
            }
        }
        result = result.replaceAll("(?i)(password|passwd|pwd)=([^&\\s,;]+)", "$1=******");
        return result.length() > 1000 ? result.substring(0, 1000) : result;
    }
}
