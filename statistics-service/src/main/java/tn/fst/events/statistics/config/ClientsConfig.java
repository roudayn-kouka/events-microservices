package tn.fst.events.statistics.config;

import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

/**
 * Clients OpenFeign des quatre services lus par la version AVANT CQRS. Dans une classe à part
 * pour qu'un @WebMvcTest (tranche web) ne tente pas de créer les clients Feign.
 */
@Configuration(proxyBeanMethods = false)
@EnableFeignClients(basePackages = "tn.fst.events.statistics.clients")
public class ClientsConfig {
}
