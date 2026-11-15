#!/usr/bin/env python3
"""
Génère la collection Postman (format v2.1) et les environnements, à partir de la liste SERVICES.
Usage : python3 postman/generate.py   -> postman/*.json (fichiers à importer dans Postman)
Chaque requête passe par la Gateway ({{gateway}}) et vérifie son code HTTP (onglet Tests).
Chaque dossier décrit la correspondance « ancien endpoint du monolithe → nouvel endpoint ».
"""
import json
import pathlib

ICI = pathlib.Path(__file__).parent

# ----------------------------------------------------------------------------- services
SERVICES = [
    {
        "nom": "participant-service",
        "origine": "feature/participant (Farah Baraket)",
        "correspondance": [
            ("POST /events/participant/add", "POST /api/participants"),
            ("GET /events/participant/all", "GET /api/participants"),
            ("GET /events/participant/{id}", "GET /api/participants/{id}"),
            ("PUT /events/participant/update/{id}", "PUT /api/participants/{id}"),
            ("PUT /events/participant/{id}/tache/{tache}", "PUT /api/participants/{id}/tache/{tache}"),
            ("DELETE /events/participant/delete/{id}", "DELETE /api/participants/{id}"),
            ("POST /events/event/addPart", "POST /api/participants"),
            ("(nouveau, pour Feign)", "GET /api/participants?ids=1,2"),
            ("(nouveau, pour calculCout)", "GET /api/participants/search?nom=&prenom=&tache="),
        ],
        "requetes": [
            ("Créer un participant (organisateur)", "POST", "/api/participants",
             {"nom": "Tounsi", "prenom": "Ahmed", "tache": "ORGANISATEUR"}, 201,
             "pm.collectionVariables.set('participantId', pm.response.json().idPart);"),
            ("Créer un participant (invitée)", "POST", "/api/participants",
             {"nom": "Ben Salah", "prenom": "Amira", "tache": "INVITE"}, 201,
             "pm.collectionVariables.set('participantId2', pm.response.json().idPart);"),
            ("Lister les participants", "GET", "/api/participants", None, 200, None),
            ("Lire un participant", "GET", "/api/participants/{{participantId}}", None, 200, None),
            ("Lire un lot (Feign)", "GET", "/api/participants?ids={{participantId}},{{participantId2}}", None, 200, None),
            ("Rechercher (calculCout)", "GET",
             "/api/participants/search?nom=Tounsi&prenom=Ahmed&tache=ORGANISATEUR", None, 200, None),
            ("Modifier un participant", "PUT", "/api/participants/{{participantId2}}",
             {"nom": "Ben Salah", "prenom": "Amira", "tache": "SERVEUR"}, 200, None),
            ("Changer la tâche", "PUT", "/api/participants/{{participantId2}}/tache/ANIMATEUR", None, 200, None),
            ("Erreur 404 : participant inconnu", "GET", "/api/participants/999999", None, 404, None),
            ("Erreur 400 : nom vide (validation)", "POST", "/api/participants",
             {"nom": "", "prenom": "X", "tache": "INVITE"}, 400, None),
            ("Erreur 400 : tâche inconnue", "PUT", "/api/participants/{{participantId}}/tache/PILOTE", None, 400, None),
        ],
    },
    {
        "nom": "logistics-service",
        "origine": "feature/logistics (Ahlem Mastouri)",
        "correspondance": [
            ("POST /events/logistics", "POST /api/logistics"),
            ("PUT /events/logistics", "PUT /api/logistics"),
            ("GET /events/logistics", "GET /api/logistics"),
            ("GET /events/logistics/{id}", "GET /api/logistics/{id} (404 si introuvable, 500 avant)"),
            ("DELETE /events/logistics/{id}", "DELETE /api/logistics/{id}"),
            ("GET /events/logistics/reserved", "GET /api/logistics/reserved"),
            ("GET /events/logistics/{id}/cout", "GET /api/logistics/{id}/cout"),
            ("(nouveau, pour Feign)", "GET /api/logistics?ids=1,2"),
            ("(nouveau, pour calculCout)", "GET /api/logistics/cout-reserve?ids=1,2"),
        ],
        "requetes": [
            ("Créer une logistique réservée", "POST", "/api/logistics",
             {"description": "Chaises", "reserve": True, "prixUnit": 12.5, "quantite": 40}, 201,
             "pm.collectionVariables.set('logisticsId', pm.response.json().idLog);"),
            ("Créer une logistique non réservée", "POST", "/api/logistics",
             {"description": "Tables", "reserve": False, "prixUnit": 30, "quantite": 10}, 201,
             "pm.collectionVariables.set('logisticsId2', pm.response.json().idLog);"),
            ("Lister la logistique", "GET", "/api/logistics", None, 200, None),
            ("Lire une logistique", "GET", "/api/logistics/{{logisticsId}}", None, 200, None),
            ("Logistique réservée", "GET", "/api/logistics/reserved", None, 200, None),
            ("Coût d'une logistique (12.5 x 40 = 500)", "GET", "/api/logistics/{{logisticsId}}/cout", None, 200,
             "pm.test('Coût = 500', () => pm.expect(pm.response.json()).to.eql(500));"),
            ("Lire un lot (Feign)", "GET", "/api/logistics?ids={{logisticsId}},{{logisticsId2}}", None, 200, None),
            ("Coût réservé d'un lot (calculCout)", "GET",
             "/api/logistics/cout-reserve?ids={{logisticsId}},{{logisticsId2}}", None, 200,
             "pm.test('Seule la logistique réservée compte', () => pm.expect(pm.response.json()).to.eql(500));"),
            ("Modifier une logistique", "PUT", "/api/logistics",
             {"idLog": "{{logisticsId2}}", "description": "Tables rondes", "reserve": True, "prixUnit": 30,
              "quantite": 10}, 200, None),
            ("Erreur 404 : logistique inconnue", "GET", "/api/logistics/999999", None, 404, None),
            ("Erreur 400 : quantité négative", "POST", "/api/logistics",
             {"description": "Chaises", "reserve": True, "prixUnit": 10, "quantite": -3}, 400, None),
        ],
    },
    {
        "nom": "event-service",
        "origine": "feature/event (Nour Mustapha), corrections de feature/feedback",
        "correspondance": [
            ("POST /events/event/addEvent", "POST /api/events (participantIds vérifiés par Feign)"),
            ("POST /events/event/addEvent/{id}", "POST /api/events/participant/{idPart}"),
            ("PUT /events/event/addAffectLog/{description}", "PUT /api/events/by-description/{description}/logistics"),
            ("GET /events/event/getLogs/{d1}/{d2}", "GET /api/events/logistics/{d1}/{d2}"),
            ("POST /events/event/addPart", "POST /api/participants (participant-service)"),
            ("(aucune lecture)", "GET /api/events, GET /api/events/{id}"),
            ("(nouveau, composition Feign)", "GET /api/events/{id}/details"),
            ("(nouveau)", "PUT /api/events/{id}, DELETE /api/events/{id}"),
            ("(nouveau)", "POST /api/events/{id}/participants/{idPart}"),
            ("(nouveau)", "POST /api/events/{id}/logistics"),
            ("calculCout @Scheduled 60 s", "idem + POST /api/events/cout/recalcul"),
        ],
        "requetes": [
            ("Créer un événement de l'organisateur", "POST", "/api/events",
             {"description": "Gala-FST-{{$timestamp}}", "dateDebut": "2026-12-10", "dateFin": "2026-12-11",
              "participantIds": ["{{participantId}}"]}, 201,
             "pm.collectionVariables.set('eventId', pm.response.json().idEvent);\n"
             "pm.collectionVariables.set('eventDescription', pm.response.json().description);"),
            ("Créer un événement pour un participant", "POST", "/api/events/participant/{{participantId2}}",
             {"description": "Atelier-{{$timestamp}}", "dateDebut": "2026-12-20", "dateFin": "2026-12-20"}, 201,
             "pm.collectionVariables.set('eventId2', pm.response.json().idEvent);"),
            ("Lister les événements", "GET", "/api/events", None, 200, None),
            ("Lire un événement", "GET", "/api/events/{{eventId}}", None, 200, None),
            ("Inscrire un participant (Feign)", "POST", "/api/events/{{eventId}}/participants/{{participantId2}}",
             None, 200,
             "pm.test('Deux inscrits', () => pm.expect(pm.response.json().participantIds.length).to.eql(2));"),
            ("Créer et rattacher une logistique (Feign)", "POST", "/api/events/{{eventId}}/logistics",
             {"description": "Chaises", "reserve": True, "prixUnit": 2.5, "quantite": 100}, 201, None),
            ("Rattacher par description (ancien addAffectLog)", "PUT",
             "/api/events/by-description/{{eventDescription}}/logistics",
             {"description": "Goodies", "reserve": False, "prixUnit": 4, "quantite": 50}, 200, None),
            ("Recalculer le coût (calculCout via Feign)", "POST", "/api/events/cout/recalcul", None, 200,
             "const ev = pm.response.json().find(e => e.idEvent === pm.collectionVariables.get('eventId'));\n"
             "pm.test('Coût = 250 (réservé seulement)', () => pm.expect(ev.cout).to.eql(250));"),
            ("Détails (3 services composés)", "GET", "/api/events/{{eventId}}/details", None, 200,
             "pm.test('Participants et logistique lus par Feign', () => {\n"
             "  pm.expect(pm.response.json().participants.length).to.eql(2);\n"
             "  pm.expect(pm.response.json().logistics.length).to.eql(2);\n});"),
            ("Logistique réservée de la période", "GET", "/api/events/logistics/2026-12-01/2026-12-31", None, 200,
             "pm.test('Réservée seulement', () => pm.response.json().forEach(l => pm.expect(l.reserve).to.be.true));"),
            ("Modifier un événement", "PUT", "/api/events/{{eventId2}}",
             {"description": "Atelier Spring Cloud", "dateDebut": "2026-12-21", "dateFin": "2026-12-22"}, 200, None),
            ("Supprimer un événement", "DELETE", "/api/events/{{eventId2}}", None, 204, None),
            ("Erreur 404 : événement inconnu", "GET", "/api/events/00000000-0000-0000-0000-000000000000", None, 404,
             None),
            ("Erreur 404 : participant inconnu (Feign)", "POST", "/api/events",
             {"description": "Fantome", "dateDebut": "2026-12-10", "dateFin": "2026-12-11",
              "participantIds": [999999]}, 404, None),
            ("Erreur 400 : date de fin avant la date de début", "POST", "/api/events",
             {"description": "Inverse", "dateDebut": "2026-12-10", "dateFin": "2026-12-01"}, 400, None),
            ("Erreur 400 : description vide (validation)", "POST", "/api/events",
             {"description": "", "dateDebut": "2026-12-10", "dateFin": "2026-12-11"}, 400, None),
        ],
    },
    {
        "nom": "feedback-service",
        "origine": "feature/feedback (ouemnaa)",
        "correspondance": [
            ("POST /events/feedback/{idEvent}/{idPart}", "POST /api/feedback/{idEvent}/{idPart} (vérifs par Feign)"),
            ("GET /events/feedback/event/{id}", "GET /api/feedback/event/{id}"),
            ("GET /events/feedback/event/{id}/moyenne", "GET /api/feedback/event/{id}/moyenne"),
            ("DELETE /events/feedback/{id}", "DELETE /api/feedback/{id}"),
            ("GET /events/event-stats (moyenne)", "GET /api/feedback/moyennes (lu par statistics-service)"),
        ],
        "requetes": [
            ("Préparer : événement terminé (event-service)", "POST", "/api/events",
             {"description": "Forum-{{$timestamp}}", "dateDebut": "2025-06-01", "dateFin": "2025-06-02",
              "participantIds": ["{{participantId}}"]}, 201,
             "pm.collectionVariables.set('feedbackEventId', pm.response.json().idEvent);"),
            ("Préparer : événement à venir (event-service)", "POST", "/api/events",
             {"description": "Futur-{{$timestamp}}", "dateDebut": "2030-01-01", "dateFin": "2030-01-02",
              "participantIds": ["{{participantId}}"]}, 201,
             "pm.collectionVariables.set('futurEventId', pm.response.json().idEvent);"),
            ("Donner un avis", "POST", "/api/feedback/{{feedbackEventId}}/{{participantId}}",
             {"note": 5, "commentaire": "Excellent forum"}, 201,
             "pm.collectionVariables.set('feedbackId', pm.response.json().id);"),
            ("Avis d'un événement", "GET", "/api/feedback/event/{{feedbackEventId}}", None, 200, None),
            ("Moyenne d'un événement", "GET", "/api/feedback/event/{{feedbackEventId}}/moyenne", None, 200,
             "pm.test('Moyenne = 5', () => pm.expect(pm.response.json().averageRating).to.eql(5));"),
            ("Moyennes de tous les événements (statistics-service)", "GET", "/api/feedback/moyennes", None, 200,
             "pm.test('Contient l\\'événement noté', () => pm.expect(pm.response.json()"
             ".some(m => m.eventId === pm.collectionVariables.get('feedbackEventId'))).to.be.true);"),
            ("Erreur 409 : deuxième avis du même participant", "POST",
             "/api/feedback/{{feedbackEventId}}/{{participantId}}", {"note": 4}, 409, None),
            ("Erreur 400 : participant non inscrit", "POST", "/api/feedback/{{feedbackEventId}}/{{participantId2}}",
             {"note": 4}, 400, None),
            ("Erreur 400 : événement pas terminé", "POST", "/api/feedback/{{futurEventId}}/{{participantId}}",
             {"note": 4}, 400, None),
            ("Erreur 400 : note hors bornes", "POST", "/api/feedback/{{feedbackEventId}}/{{participantId}}",
             {"note": 6}, 400, None),
            ("Erreur 400 : note décimale", "POST", "/api/feedback/{{feedbackEventId}}/{{participantId}}",
             {"note": 1.5}, 400, None),
            ("Erreur 404 : événement inconnu (Feign)", "POST",
             "/api/feedback/00000000-0000-0000-0000-000000000000/{{participantId}}", {"note": 4}, 404, None),
            ("Erreur 404 : participant inconnu (Feign)", "POST", "/api/feedback/{{feedbackEventId}}/999999",
             {"note": 4}, 404, None),
            ("Supprimer un avis", "DELETE", "/api/feedback/{{feedbackId}}", None, 204, None),
            ("Erreur 404 : avis déjà supprimé", "DELETE", "/api/feedback/{{feedbackId}}", None, 404, None),
        ],
    },
]


# ----------------------------------------------------------------------------- génération
def requete(nom, methode, chemin, corps, attendu, script_suite):
    tests = [f"pm.test('HTTP {attendu}', () => pm.response.to.have.status({attendu}));"]
    if attendu < 300:
        tests.append("pm.test('Réponse produite par le service (X-Instance-Id)', () => "
                     "pm.response.to.have.header('X-Instance-Id'));")
    if script_suite:
        tests.append(script_suite)
    chemin_brut = "{{gateway}}" + chemin
    item = {
        "name": nom,
        "event": [{"listen": "test", "script": {"type": "text/javascript", "exec": tests}}],
        "request": {
            "method": methode,
            "header": [{"key": "Content-Type", "value": "application/json"}] if corps is not None else [],
            "url": {"raw": chemin_brut, "host": ["{{gateway}}"],
                    "path": [p for p in chemin.split("?")[0].split("/") if p]},
        },
    }
    if "?" in chemin:
        item["request"]["url"]["query"] = [
            {"key": k, "value": v} for k, v in (q.split("=", 1) for q in chemin.split("?", 1)[1].split("&"))]
    if corps is not None:
        item["request"]["body"] = {"mode": "raw", "raw": json.dumps(corps, ensure_ascii=False, indent=2)}
    return item


def dossier(service):
    lignes = ["| Monolithe (/events, port 8089) | Microservices (via la Gateway, port 8088) |", "|---|---|"]
    lignes += [f"| `{a}` | `{n}` |" for a, n in service["correspondance"]]
    description = (f"**{service['nom']}** — repris de `{service['origine']}`.\n\n"
                   "### Ancien endpoint → nouvel endpoint via la Gateway\n\n" + "\n".join(lignes))
    return {"name": service["nom"], "description": description,
            "item": [requete(*r) for r in service["requetes"]]}


collection = {
    "info": {
        "name": "Events FST — microservices Spring Cloud",
        "description": "Toutes les requêtes passent par l'API Gateway ({{gateway}}). Générée par postman/generate.py.",
        "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
    },
    "item": [dossier(s) for s in SERVICES],
    "variable": [{"key": k, "value": "1"} for k in
                 ("participantId", "participantId2", "logisticsId", "logisticsId2", "eventId", "eventId2",
                  "eventDescription", "feedbackEventId", "futurEventId", "feedbackId")],
}


def environnement(nom, gateway):
    return {"name": nom, "values": [{"key": "gateway", "value": gateway, "type": "default", "enabled": True}],
            "_postman_variable_scope": "environment"}


(ICI / "events-microservices.postman_collection.json").write_text(
    json.dumps(collection, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
(ICI / "local.postman_environment.json").write_text(
    json.dumps(environnement("Events — local", "http://localhost:8088"), ensure_ascii=False, indent=2) + "\n",
    encoding="utf-8")
(ICI / "aws.postman_environment.json").write_text(
    json.dumps(environnement("Events — AWS", "http://IP_PUBLIQUE_EC2:8088"), ensure_ascii=False, indent=2) + "\n",
    encoding="utf-8")
total = sum(len(s["requetes"]) for s in SERVICES)
print(f"Collection générée : {len(SERVICES)} dossier(s), {total} requête(s).")
