package tn.fst.events.event.config;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandExecutionException;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.messaging.InterceptorChain;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.modelling.command.AggregateNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import tn.fst.events.event.commands.ErreurCommande;
import tn.fst.events.event.commands.RegleMetierException;

/**
 * Transport des erreurs métier à travers Axon Server : l'exception levée par l'agrégat est
 * remplacée par une CommandExecutionException qui porte un ErreurCommande (code + message) dans
 * ses « détails » — la seule partie d'une erreur, avec le message, qui traverse le réseau.
 * Même traitement pour un agrégat absent de l'event store (chargé à l'intérieur de cette chaîne) :
 * sans cela, Axon Server le transmet « sans détails » et l'appelant ne peut pas répondre 404.
 */
@Configuration(proxyBeanMethods = false)
public class AxonConfig {

    @Autowired
    void enregistrerTraductionDesErreurs(CommandBus commandBus) {
        commandBus.registerHandlerInterceptor(new TraductionErreursMetier());
    }

    static class TraductionErreursMetier implements MessageHandlerInterceptor<CommandMessage<?>> {
        @Override
        public Object handle(UnitOfWork<? extends CommandMessage<?>> unitOfWork, InterceptorChain chain)
                throws Exception {
            try {
                return chain.proceed();
            } catch (RegleMetierException e) {
                throw new CommandExecutionException(e.getMessage(), e, new ErreurCommande(e.getStatus(), e.getMessage()));
            } catch (AggregateNotFoundException e) {
                String message = "Event not found with id " + e.getAggregateIdentifier();
                throw new CommandExecutionException(message, e, new ErreurCommande(404, message));
            }
        }
    }
}
