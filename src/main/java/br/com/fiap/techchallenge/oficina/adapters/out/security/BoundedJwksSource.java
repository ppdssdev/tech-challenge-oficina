package br.com.fiap.techchallenge.oficina.adapters.out.security;

import com.github.benmanes.caffeine.cache.Ticker;
import com.nimbusds.jose.KeySourceException;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.net.URI;
import java.text.ParseException;
import java.time.Duration;
import java.util.List;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestOperations;

/** One trusted snapshot, no per-token state. All refresh attempts share a bounded cooldown. */
public final class BoundedJwksSource implements JWKSource<SecurityContext> {
    private final RestOperations http;
    private final URI uri;
    private final Ticker ticker;
    private final long ttlNanos;
    private final long refreshNanos;
    private JWKSet snapshot;
    private long fetchedAt;
    private long attemptedAt;
    private boolean attempted;

    public BoundedJwksSource(RestOperations http, URI uri, Duration ttl, Ticker ticker) {
        if (ttl.isNegative() || ttl.isZero() || ttl.compareTo(Duration.ofSeconds(300)) > 0)
            throw new IllegalArgumentException("JWKS TTL must be positive and at most 300 seconds");
        this.http = http;
        this.uri = uri;
        this.ticker = ticker;
        this.ttlNanos = ttl.toNanos();
        this.refreshNanos = Math.min(ttlNanos, Duration.ofSeconds(5).toNanos());
    }

    @Override
    public synchronized List<JWK> get(JWKSelector selector, SecurityContext context) throws KeySourceException {
        long now = ticker.read();
        if (snapshot != null && now - fetchedAt >= ttlNanos) snapshot = null;
        if (snapshot != null) {
            var matches = selector.select(snapshot);
            if (!matches.isEmpty()) return matches;
        }
        // A missing key never authenticates. A failed fetch also consumes the refresh allowance.
        if (attempted && now - attemptedAt < refreshNanos) return List.of();
        attempted = true;
        attemptedAt = now;
        try {
            String body = http.getForObject(uri, String.class);
            if (body == null) throw new KeySourceException("Empty JWKS response");
            var fetched = JWKSet.parse(body);
            snapshot = fetched;
            fetchedAt = ticker.read();
            return selector.select(fetched);
        } catch (RestClientException | ParseException failure) {
            throw new KeySourceException("Unable to retrieve trusted JWKS", failure);
        }
    }
}
