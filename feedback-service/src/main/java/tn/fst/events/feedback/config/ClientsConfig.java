package tn.fst.events.feedback.config;

import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

/**
 * Clients OpenFeign (event-service, participant-service). Dans une classe à part pour qu'un
 * @WebMvcTest (tranche web) ne tente pas de créer les clients Feign.
 */
@Configuration(proxyBeanMethods = false)
@EnableFeignClients(basePackages = "tn.fst.events.feedback.clients")
public class ClientsConfig {
}
