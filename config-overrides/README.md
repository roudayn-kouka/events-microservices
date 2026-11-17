# config-overrides/ — surcharges à chaud (non versionnées)

Le Config Server lit ce dossier **avant** `config-repo/` : un fichier `<service>.yml` posé ici l'emporte sur la configuration de référence.

Seuls les scripts `scripts/pannes/` y écrivent, pour activer une panne (latence, URL erronée…), puis l'enlèvent. La configuration de référence reste intacte et aucun commit n'est créé. Après chaque changement : `POST /actuator/refresh` sur les instances concernées.

Les fichiers `*.yml` de ce dossier sont ignorés par Git.
