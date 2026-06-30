package br.com.fiap.techchallenge.oficina.domain.service;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public final class WorkOrderCodeGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    private WorkOrderCodeGenerator() {
    }

    public static String generate() {
        int suffix = RANDOM.nextInt(900000) + 100000;
        return "OS-" + LocalDate.now().format(FORMATTER) + "-" + suffix;
    }
}
