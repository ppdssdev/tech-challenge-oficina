package br.com.fiap.techchallenge.oficina.adapters.out.persistence;

import br.com.fiap.techchallenge.oficina.application.port.out.TransactionPort;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class SpringTransactionAdapter implements TransactionPort {
    private final TransactionTemplate template;

    public SpringTransactionAdapter(TransactionTemplate template) { this.template = template; }

    @Override
    public <T> T required(Supplier<T> action) {
        return template.execute(status -> action.get());
    }
}
