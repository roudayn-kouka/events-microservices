# Scénarios de panne (phase 4)

Chaque script crée la panne, montre la réaction des services, puis **rétablit tout seul** la situation (même interrompu : `trap`). Prérequis : `make up`, `make seed`.

```bash
make panne N=1     # ou : bash scripts/pannes/1-disponibilite.sh
```

| # | Script | Panne | Comment elle est créée | Ce que montrent les services |
|---|---|---|---|---|
| 1 | `1-disponibilite.sh` | Disponibilité | `docker compose stop participant-service` | Retry, puis circuit `participant-service` **OPEN** dans event-service ; vue détaillée dégradée (`avertissements`) ; inscription refusée (503 « circuit ouvert ») ; HALF_OPEN puis CLOSED après le redémarrage |
| 2 | `2-temps-de-reponse.sh` | Temps de réponse > 5 s | `panne.latence-ms: 7000` posé **à chaud** dans participant-service (Config Server + `/actuator/refresh`) | **TimeLimiter** 5 s : 504 « pas de réponse en 5000 ms » ; la lecture retentée 3 fois se dégrade |
| 3 | `3-reponse-incorrecte.sh` | Réponse incorrecte | `panne.reponse-corrompue: true` (à chaud) : JSON tronqué en HTTP 200 | réponse **rejetée** (502 « réponse incorrecte, rejetée ») ; rien d'invalide n'est enregistré |
| 4 | `4-erreur-reseau.sh` | Erreur réseau | `events.clients.participant-service.url-forcee: http://participant-introuvable:8102` dans event-service (à chaud) | vraie erreur réseau (hôte inconnu), Retry ×3, « injoignable (erreur réseau) » ; **correction à chaud** par `/actuator/refresh` |
| 5 | `5-dependance-externe.sh` | Dépendance externe | compte MySQL `participant_user` verrouillé, connexions coupées | santé de participant-service **DOWN** (`db`) ; ses réponses en erreur ouvrent le circuit d'event-service ; retour à la normale au déverrouillage |

**Où voir les circuits** :
- `curl -s localhost:8101/actuator/circuitbreakers | jq` : état de chaque circuit d'event-service ;
- `curl -s localhost:8101/actuator/circuitbreakerevents | jq` : transitions CLOSED → OPEN → HALF_OPEN → CLOSED ;
- tableau de bord Grafana, à la phase 6.

**Réglages** (`config-repo/resilience-feign.yml`) : Retry 3 × 500 ms (lectures seulement) ; RateLimiter 5/s ; CircuitBreaker OPEN à 50 % d'échecs sur 10 appels (5 au minimum), 10 s ouvert, 3 appels d'essai ; TimeLimiter 5 s.
