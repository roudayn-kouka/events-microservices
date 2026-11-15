package tn.fst.events.logistics.services;

import java.util.Collection;
import java.util.List;
import tn.fst.events.logistics.entities.Logistics;

public interface ILogisticsService {

    Logistics addLogistics(Logistics logistics);

    Logistics updateLogistics(Logistics logistics);

    void deleteLogistics(int idLog);

    Logistics getLogisticsById(int idLog);

    List<Logistics> getAllLogistics();

    List<Logistics> getReservedLogistics();

    float calculerCoutTotal(int idLog);

    /** Nouveau : lecture par lot pour les appels Feign. */
    List<Logistics> getLogisticsByIds(Collection<Integer> ids);

    /** Nouveau : somme prixUnit × quantité des logistiques RÉSERVÉES parmi ces ID (calculCout). */
    float calculerCoutReserve(Collection<Integer> ids);
}
