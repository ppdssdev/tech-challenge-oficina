package br.com.fiap.techchallenge.oficina.adapters.out.security;

import java.util.UUID;
public record CustomerIdentity(UUID customerId) implements java.security.Principal {
    @Override public String getName() { return customerId.toString(); }
}
