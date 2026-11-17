package tn.fst.events.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Point d'entrée unique des API. Les routes (lb://<service>, résolues dans Eureka)
 * et le CORS sont définis dans config-repo/api-gateway.yml.
 */
@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
