package br.com.fiap.techchallenge.oficina.domain.service;

public final class VehiclePlateValidator {

    private static final String OLD_BRAZILIAN_PATTERN = "^[A-Z]{3}[0-9]{4}$";
    private static final String MERCOSUR_PATTERN = "^[A-Z]{3}[0-9][A-Z][0-9]{2}$";

    private VehiclePlateValidator() {
    }

    public static String normalize(String plate) {
        return plate == null ? null : plate.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
    }

    public static boolean isValid(String plate) {
        String normalized = normalize(plate);
        if (normalized == null) {
            return false;
        }
        return normalized.matches(OLD_BRAZILIAN_PATTERN) || normalized.matches(MERCOSUR_PATTERN);
    }
}
