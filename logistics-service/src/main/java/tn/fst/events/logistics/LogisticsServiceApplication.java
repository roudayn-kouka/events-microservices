package tn.fst.events.logistics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Logistique des événements (matériel, réservation, coût). Base dédiée : db_logistics. */
@SpringBootApplication
public class LogisticsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(LogisticsServiceApplication.class, args);
    }
}
