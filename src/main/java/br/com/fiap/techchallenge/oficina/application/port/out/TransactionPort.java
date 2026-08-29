package br.com.fiap.techchallenge.oficina.application.port.out;

import java.util.function.Supplier;

public interface TransactionPort {
    <T> T required(Supplier<T> action);

    static TransactionPort direct() {
        return new TransactionPort() {
            @Override
            public <T> T required(Supplier<T> action) {
                return action.get();
            }
        };
    }
}
