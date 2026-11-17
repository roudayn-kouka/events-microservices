package tn.fst.events.participant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Participants et tâches. Base dédiée : db_participant. */
@SpringBootApplication
public class ParticipantServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ParticipantServiceApplication.class, args);
    }
}
