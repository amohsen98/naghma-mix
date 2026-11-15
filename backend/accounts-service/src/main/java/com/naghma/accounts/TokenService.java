package com.naghma.accounts;

import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Issues RS256 JWTs. The other services only hold the public key, so they can verify
 * tokens and read the tier claim without calling this service on every request.
 */
@ApplicationScoped
public class TokenService {

    @ConfigProperty(name = "mp.jwt.verify.issuer")
    String issuer;

    @ConfigProperty(name = "naghma.jwt.lifetime", defaultValue = "PT12H")
    Duration lifetime;

    public String issue(User user) {
        Set<String> groups = new HashSet<>(Set.of("user"));
        if (user.admin) {
            groups.add("admin");
        }
        return Jwt.issuer(issuer)
                .subject(user.id.toString())
                .upn(user.email)
                .groups(groups)
                .claim("name", user.displayName)
                .claim("tier", user.tier.name())
                .claim("locale", user.locale)
                .expiresIn(lifetime)
                .sign();
    }
}
