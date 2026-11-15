package tn.fst.events.event;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Événements : création, affectation des participants et de la logistique, calcul du coût.
 * Base dédiée : db_event. Les participants et la logistique sont dans d'autres services :
 * ils sont référencés par leur identifiant et lus par OpenFeign (voir config.ClientsConfig).
 */
@SpringBootApplication
public class EventServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(EventServiceApplication.class, args);
    }
}
