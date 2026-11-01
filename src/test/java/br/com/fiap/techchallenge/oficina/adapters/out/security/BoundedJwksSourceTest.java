package br.com.fiap.techchallenge.oficina.adapters.out.security;

import com.nimbusds.jose.KeySourceException;
import com.nimbusds.jose.jwk.*;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.ResourceAccessException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class BoundedJwksSourceTest {
    final RestOperations http = mock(RestOperations.class);
    final URI uri = URI.create("https://issuer.test/jwks");
    final AtomicLong time = new AtomicLong();
    final BoundedJwksSource source = new BoundedJwksSource(http, uri, Duration.ofSeconds(30), time::get);
    final JWKSelector unknown = new JWKSelector(new JWKMatcher.Builder().keyID("missing").build());

    @Test void failedRequestsConsumeCooldownAndRecoverAtExactBoundary() throws Exception {
        when(http.getForObject(uri, String.class)).thenThrow(new ResourceAccessException("offline"))
            .thenReturn("{\"keys\":[]}");
        assertThatThrownBy(() -> source.get(unknown, null)).isInstanceOf(KeySourceException.class);
        time.set(Duration.ofSeconds(5).toNanos() - 1);
        for (int i = 0; i < 100; i++) assertThat(source.get(unknown, null)).isEmpty();
        verify(http).getForObject(uri, String.class);
        time.incrementAndGet();
        assertThat(source.get(unknown, null)).isEmpty();
        verify(http, times(2)).getForObject(uri, String.class);
    }

    @Test void malformedAndEmptyResponsesFailClosedAndAreThrottled() throws Exception {
        when(http.getForObject(uri, String.class)).thenReturn("not json", null);
        assertThatThrownBy(() -> source.get(unknown, null)).isInstanceOf(KeySourceException.class);
        assertThat(source.get(unknown, null)).isEmpty();
        time.set(Duration.ofSeconds(5).toNanos());
        assertThatThrownBy(() -> source.get(unknown, null)).isInstanceOf(KeySourceException.class);
        assertThat(source.get(unknown, null)).isEmpty();
        verify(http, times(2)).getForObject(uri, String.class);
    }

    @Test void shortTtlAlsoBoundsRefreshCooldown() throws Exception {
        var shortCache = new BoundedJwksSource(http, uri, Duration.ofSeconds(1), time::get);
        when(http.getForObject(uri, String.class)).thenReturn("{\"keys\":[]}");
        assertThat(shortCache.get(unknown, null)).isEmpty();
        time.set(Duration.ofSeconds(1).toNanos());
        assertThat(shortCache.get(unknown, null)).isEmpty();
        verify(http, times(2)).getForObject(uri, String.class);
    }

    @Test void rejectsUnboundedRetention() {
        for (var ttl : new Duration[]{Duration.ZERO, Duration.ofSeconds(-1), Duration.ofSeconds(301)})
            assertThatThrownBy(() -> new BoundedJwksSource(http, uri, ttl, time::get))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
