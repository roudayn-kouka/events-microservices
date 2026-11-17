package tn.fst.events.event.config;

import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Clients OpenFeign (participant-service, logistics-service) et planification de calculCout.
 * Dans une classe à part, et non sur la classe principale, pour qu'un @WebMvcTest (tranche web)
 * ne tente pas de créer les clients Feign sans leur auto-configuration.
 */
@Configuration(proxyBeanMethods = false)
@EnableFeignClients(basePackages = "tn.fst.events.event.clients")
@EnableScheduling
public class ClientsConfig {
}
