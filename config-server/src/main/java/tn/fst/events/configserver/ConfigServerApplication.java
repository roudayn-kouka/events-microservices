package tn.fst.events.configserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

/**
 * Serveur de configuration centralisée.
 * Sources, par ordre de priorité (profil « composite ») :
 * 1. config-overrides/ (dossier non versionné) : bascules à chaud des scénarios de panne ;
 * 2. config-repo/ du dépôt Git : la configuration de référence, un fichier par service.
 */
@SpringBootApplication
@EnableConfigServer
public class ConfigServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }
}
