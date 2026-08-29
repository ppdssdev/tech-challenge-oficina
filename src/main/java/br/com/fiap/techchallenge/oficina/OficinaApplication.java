package br.com.fiap.techchallenge.oficina;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OficinaApplication {
    public static void main(String[] args) {
        SpringApplication.run(OficinaApplication.class, args);
    }
}
