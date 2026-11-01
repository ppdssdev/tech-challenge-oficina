package br.com.fiap.techchallenge.oficina.adapters.in.web;

import br.com.fiap.techchallenge.oficina.adapters.out.security.CustomerIdentity;
import br.com.fiap.techchallenge.oficina.application.port.in.GetWorkOrderUseCase;
import java.util.UUID;
import br.com.fiap.techchallenge.oficina.application.port.in.result.CustomerWorkOrderResult;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/customer/work-orders")
public class CustomerWorkOrderController {
    private final GetWorkOrderUseCase orders;
    public CustomerWorkOrderController(GetWorkOrderUseCase orders) { this.orders = orders; }
    @GetMapping("/{id}")
    public CustomerWorkOrderResult get(@PathVariable UUID id, @AuthenticationPrincipal CustomerIdentity identity) {
        return orders.getForCustomer(id, identity.customerId());
    }
}
