package br.com.fiap.techchallenge.oficina.configuration;

import io.micrometer.common.KeyValues;
import io.micrometer.observation.transport.ReceiverContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.handler.PropagatingReceiverTracingObservationHandler;
import io.micrometer.tracing.propagation.Propagator;
import org.springframework.core.annotation.Order;
import static org.springframework.boot.actuate.autoconfigure.tracing.MicrometerTracingAutoConfiguration.RECEIVER_TRACING_OBSERVATION_HANDLER_ORDER;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.observation.DefaultServerRequestObservationConvention;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.http.server.observation.ServerRequestObservationConvention;

@Configuration(proxyBeanMethods = false)
public class TracingConfiguration {
    @Bean
    @Order(RECEIVER_TRACING_OBSERVATION_HANDLER_ORDER)
    PropagatingReceiverTracingObservationHandler<ReceiverContext<?>> privateReceiverTracingHandler(
            Tracer tracer, Propagator propagator) {
        return new PropagatingReceiverTracingObservationHandler<>(tracer, propagator) {
            @Override
            public void onError(ReceiverContext<?> context) {
                if (context.getError() != null) {
                    // Keep the error status, but never export raw exception messages/causes/stacks.
                    var safe = new IllegalStateException(context.getError().getClass().getSimpleName());
                    safe.setStackTrace(new StackTraceElement[0]);
                    getRequiredSpan(context).error(safe);
                }
            }
        };
    }

    @Bean
    ServerRequestObservationConvention privateServerRequestObservationConvention() {
        return new DefaultServerRequestObservationConvention() {
            @Override
            public KeyValues getHighCardinalityKeyValues(ServerRequestObservationContext context) {
                // Raw URLs/query strings may contain customer documents or decision tokens.
                return KeyValues.empty();
            }
        };
    }
}
