package br.com.fiap.techchallenge.oficina.domain.model.customer;

import br.com.fiap.techchallenge.oficina.domain.exception.BusinessException;
import br.com.fiap.techchallenge.oficina.domain.service.DocumentValidator;
import java.util.Objects;

public final class DocumentNumber {

    private final DocumentType type;
    private final String value;

    public DocumentNumber(DocumentType type, String value) {
        if (type == null) {
            throw new BusinessException("Tipo do documento é obrigatório.");
        }

        String normalizedValue = DocumentValidator.onlyDigits(value);
        if (!DocumentValidator.isValid(type, normalizedValue)) {
            throw new BusinessException("Documento inválido para o tipo informado.");
        }

        this.type = type;
        this.value = normalizedValue;
    }

    public DocumentType getType() {
        return type;
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DocumentNumber that)) {
            return false;
        }
        return type == that.type && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, value);
    }

    @Override
    public String toString() {
        return value;
    }
}
