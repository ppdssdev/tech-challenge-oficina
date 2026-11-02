package br.com.fiap.techchallenge.oficina.adapters.out.security;

import br.com.fiap.techchallenge.oficina.application.port.out.TransactionPort;
import br.com.fiap.techchallenge.oficina.application.port.out.WorkOrderRepositoryPort;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/** Authorization for the existing customer-facing HTTP operations. */
@Component("workOrderAuthorization")
public class WorkOrderAuthorization {
    private final WorkOrderRepositoryPort workOrders;
    private final TransactionPort transactions;

    public WorkOrderAuthorization(WorkOrderRepositoryPort workOrders, TransactionPort transactions) {
        this.workOrders = workOrders;
        this.transactions = transactions;
    }

    public boolean canAccess(Authentication authentication, String code) {
        if (authentication == null || !authentication.isAuthenticated()) return false;
        if (hasAuthority(authentication, "ROLE_ADMIN")) return true;
        if (!hasAuthority(authentication, "ROLE_CUSTOMER")) return false;

        // Only the authenticated principal identifies the customer; request fields never do.
        UUID customerId;
        try {
            customerId = UUID.fromString(authentication.getName());
            if (!customerId.toString().equalsIgnoreCase(authentication.getName())) return false;
        } catch (IllegalArgumentException | NullPointerException exception) {
            return false;
        }
        if (code == null || code.isBlank()) return false;
        return transactions.required(() -> workOrders.findByCode(code)
            .filter(order -> order.getCustomer() != null)
            .map(order -> customerId.equals(order.getCustomer().getId()))
            .orElse(false));
    }

    private boolean hasAuthority(Authentication authentication, String authority) {
        return authentication.getAuthorities().stream()
            .anyMatch(granted -> authority.equals(granted.getAuthority()));
    }
}
