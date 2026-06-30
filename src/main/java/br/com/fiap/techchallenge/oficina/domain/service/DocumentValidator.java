package br.com.fiap.techchallenge.oficina.domain.service;

import br.com.fiap.techchallenge.oficina.domain.model.customer.DocumentType;
import java.util.Objects;
import java.util.stream.IntStream;

public final class DocumentValidator {

    private DocumentValidator() {
    }

    public static String onlyDigits(String value) {
        return value == null ? null : value.replaceAll("\\D", "");
    }

    public static boolean isValid(DocumentType type, String document) {
        if (type == null) {
            return false;
        }
        return switch (type) {
            case CPF -> isValidCpf(document);
            case CNPJ -> isValidCnpj(document);
        };
    }

    public static boolean isValidCpf(String value) {
        String cpf = onlyDigits(value);
        if (cpf == null || cpf.length() != 11 || hasSameDigits(cpf)) {
            return false;
        }

        int firstCheck = calculateCpfDigit(cpf, 9);
        int secondCheck = calculateCpfDigit(cpf, 10);

        return firstCheck == Character.getNumericValue(cpf.charAt(9))
            && secondCheck == Character.getNumericValue(cpf.charAt(10));
    }

    public static boolean isValidCnpj(String value) {
        String cnpj = onlyDigits(value);
        if (cnpj == null || cnpj.length() != 14 || hasSameDigits(cnpj)) {
            return false;
        }

        int firstCheck = calculateCnpjDigit(cnpj, 12);
        int secondCheck = calculateCnpjDigit(cnpj, 13);

        return firstCheck == Character.getNumericValue(cnpj.charAt(12))
            && secondCheck == Character.getNumericValue(cnpj.charAt(13));
    }

    private static int calculateCpfDigit(String cpf, int length) {
        int sum = 0;
        for (int i = 0; i < length; i++) {
            sum += Character.getNumericValue(cpf.charAt(i)) * (length + 1 - i);
        }
        int result = 11 - (sum % 11);
        return result >= 10 ? 0 : result;
    }

    private static int calculateCnpjDigit(String cnpj, int length) {
        int[] weights = length == 12
            ? new int[]{5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2}
            : new int[]{6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        int sum = IntStream.range(0, length)
            .map(i -> Character.getNumericValue(cnpj.charAt(i)) * weights[i])
            .sum();
        int result = sum % 11;
        return result < 2 ? 0 : 11 - result;
    }

    private static boolean hasSameDigits(String value) {
        return Objects.requireNonNull(value).chars().distinct().count() == 1;
    }
}
