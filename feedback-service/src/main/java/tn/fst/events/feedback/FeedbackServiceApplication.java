package tn.fst.events.feedback;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Avis (feedback) sur les événements terminés. Base dédiée : db_feedback.
 * L'événement et le participant sont vérifiés par OpenFeign (voir config.ClientsConfig) :
 * ils appartiennent à event-service et participant-service.
 */
@SpringBootApplication
public class FeedbackServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FeedbackServiceApplication.class, args);
    }
}
