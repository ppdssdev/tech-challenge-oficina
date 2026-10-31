package br.com.fiap.techchallenge.oficina.adapters.in.web;

import java.util.regex.Pattern;

final class PiiMasker {
    private static final Pattern CPF = Pattern.compile("(?<!\\d)(\\d{3})[.-]?(\\d{3})[.-]?(\\d{3})-?(\\d{2})(?!\\d)");
    private static final Pattern CNPJ = Pattern.compile("(?<!\\d)(\\d{2})[.]?(\\d{3})[.]?(\\d{3})/?(\\d{4})-?(\\d{2})(?!\\d)");

    private PiiMasker() {
    }

    static String mask(String value) {
        if (value == null) {
            return null;
        }
        String withoutCnpj = CNPJ.matcher(value).replaceAll("$1.***.***/****-$5");
        return CPF.matcher(withoutCnpj).replaceAll("$1.***.***-$4");
    }
}
