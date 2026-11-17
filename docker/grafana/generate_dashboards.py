#!/usr/bin/env python3
"""
Génère les 3 tableaux de bord Grafana (phase 6) dans docker/grafana/dashboards/*.json.
Usage : python3 docker/grafana/generate_dashboards.py   (les JSON produits sont versionnés)
Un générateur plutôt que du JSON écrit à la main : mêmes réglages partout, requêtes relues facilement.
"""
import json
import pathlib

ICI = pathlib.Path(__file__).parent / "dashboards"
DS = {"type": "prometheus", "uid": "prometheus"}
HORS_ACTUATOR = 'uri!~"/actuator.*"'


class Tableau:
    def __init__(self, uid, titre, description):
        self.uid, self.titre, self.description = uid, titre, description
        self.panneaux, self.x, self.y, self.hauteur_ligne = [], 0, 0, 0

    def _place(self, largeur, hauteur):
        if self.x + largeur > 24:
            self.x, self.y = 0, self.y + self.hauteur_ligne
            self.hauteur_ligne = 0
        pos = {"x": self.x, "y": self.y, "w": largeur, "h": hauteur}
        self.x += largeur
        self.hauteur_ligne = max(self.hauteur_ligne, hauteur)
        return pos

    def ajouter(self, type_, titre, cibles, largeur=12, hauteur=8, unite=None, description="", options=None, seuils=None):
        champs = {"defaults": {"unit": unite} if unite else {}, "overrides": []}
        if seuils:
            champs["defaults"]["thresholds"] = {"mode": "absolute", "steps": seuils}
            champs["defaults"]["color"] = {"mode": "thresholds"}
        self.panneaux.append({
            "id": len(self.panneaux) + 1, "type": type_, "title": titre, "description": description,
            "datasource": DS, "gridPos": self._place(largeur, hauteur), "fieldConfig": champs,
            "options": options or {},
            "targets": [{"datasource": DS, "refId": chr(65 + i), "expr": expr, "legendFormat": legende}
                        for i, (expr, legende) in enumerate(cibles)],
        })

    def json(self):
        return {"uid": self.uid, "title": self.titre, "description": self.description, "tags": ["events-fst"],
                "timezone": "browser", "schemaVersion": 39, "version": 1, "refresh": "10s",
                "time": {"from": "now-30m", "to": "now"}, "panels": self.panneaux}


# ------------------------------------------------------------------ 1. JVM et HTTP
jvm = Tableau("events-jvm-http", "Events — JVM et HTTP", "Tous les services, découverts par Eureka")
jvm.ajouter("stat", "Instances UP", [('sum(up{job="services"})', "instances")], 6, 4,
            seuils=[{"color": "red", "value": None}, {"color": "green", "value": 9}])
jvm.ajouter("stat", "Requêtes HTTP / s", [(f'sum(rate(http_server_requests_seconds_count{{{HORS_ACTUATOR}}}[1m]))', "")], 6, 4, "reqps")
jvm.ajouter("stat", "Erreurs 5xx / s", [(f'sum(rate(http_server_requests_seconds_count{{status=~"5..",{HORS_ACTUATOR}}}[1m])) or vector(0)', "")], 6, 4, "reqps",
            seuils=[{"color": "green", "value": None}, {"color": "red", "value": 0.2}])
jvm.ajouter("stat", "Latence p95 (toutes requêtes)", [(f'histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{{{HORS_ACTUATOR}}}[1m])))', "")], 6, 4, "s",
            seuils=[{"color": "green", "value": None}, {"color": "orange", "value": 1}, {"color": "red", "value": 2}])
jvm.ajouter("timeseries", "Requêtes HTTP / s par application",
            [(f'sum by (application) (rate(http_server_requests_seconds_count{{{HORS_ACTUATOR}}}[1m]))', "{{application}}")], unite="reqps")
jvm.ajouter("timeseries", "Latence p95 par application",
            [(f'histogram_quantile(0.95, sum by (le, application) (rate(http_server_requests_seconds_bucket{{{HORS_ACTUATOR}}}[1m])))', "{{application}}")], unite="s")
jvm.ajouter("timeseries", "Réponses par code HTTP",
            [(f'sum by (status) (rate(http_server_requests_seconds_count{{{HORS_ACTUATOR}}}[1m]))', "HTTP {{status}}")], unite="reqps")
jvm.ajouter("timeseries", "Mémoire heap par instance",
            [('sum by (instance) (jvm_memory_used_bytes{area="heap"})', "{{instance}}")], unite="bytes")
jvm.ajouter("timeseries", "CPU par instance", [("process_cpu_usage", "{{instance}}")], unite="percentunit")
jvm.ajouter("timeseries", "Threads actifs par instance", [("jvm_threads_live_threads", "{{instance}}")])
jvm.ajouter("timeseries", "Temps passé en GC (par seconde)",
            [("sum by (instance) (rate(jvm_gc_pause_seconds_sum[1m]))", "{{instance}}")], unite="s")
jvm.ajouter("timeseries", "Connexions MySQL actives (HikariCP)",
            [("sum by (instance) (hikaricp_connections_active)", "{{instance}}")])

# ------------------------------------------------------------------ 2. Resilience4j
r4j = Tableau("events-resilience4j", "Events — Resilience4j",
              "Circuits, Retry, RateLimiter et TimeLimiter des appels Feign (make panne N=1..5)")
r4j.ajouter("stat", "Circuits ouverts", [('sum(resilience4j_circuitbreaker_state{state="open"}) or vector(0)', "")], 6, 5,
            seuils=[{"color": "green", "value": None}, {"color": "red", "value": 1}])
r4j.ajouter("stat", "Circuits mi-ouverts (HALF_OPEN)", [('sum(resilience4j_circuitbreaker_state{state="half_open"}) or vector(0)', "")], 6, 5,
            seuils=[{"color": "green", "value": None}, {"color": "orange", "value": 1}])
r4j.ajouter("table", "État de chaque circuit (1 = état actuel)",
            [('max by (application, name, state) (resilience4j_circuitbreaker_state) == 1', "")], 12, 5,
            options={"showHeader": True})
r4j.panneaux[-1]["targets"][0]["format"] = "table"
r4j.panneaux[-1]["targets"][0]["instant"] = True
r4j.ajouter("timeseries", "Circuit OUVERT (1) au fil du temps",
            [('max by (application, name) (resilience4j_circuitbreaker_state{state="open"})', "{{application}} → {{name}}"),
             ('max by (application, name) (resilience4j_circuitbreaker_state{state="half_open"}) * 0.5', "{{application}} → {{name}} (HALF_OPEN)")],
            description="1 : OPEN ; 0,5 : HALF_OPEN ; 0 : CLOSED")
r4j.ajouter("timeseries", "Taux d'échec des circuits (%)",
            [("max by (application, name) (resilience4j_circuitbreaker_failure_rate)", "{{application}} → {{name}}")], unite="percent")
r4j.ajouter("timeseries", "Appels par résultat (CircuitBreaker)",
            [("sum by (name, kind) (rate(resilience4j_circuitbreaker_calls_seconds_count[1m]))", "{{name}} {{kind}}")], unite="reqps")
r4j.ajouter("timeseries", "Appels refusés (circuit ouvert)",
            [("sum by (application, name) (rate(resilience4j_circuitbreaker_not_permitted_calls_total[1m]))", "{{application}} → {{name}}")], unite="reqps")
r4j.ajouter("timeseries", "Retry : appels par résultat",
            [("sum by (name, kind) (rate(resilience4j_retry_calls_total[1m]))", "{{name}} {{kind}}")], unite="reqps")
r4j.ajouter("timeseries", "TimeLimiter : appels par résultat",
            [("sum by (name, kind) (rate(resilience4j_timelimiter_calls_total[1m]))", "{{name}} {{kind}}")], unite="reqps")
r4j.ajouter("timeseries", "Appels Feign : durée moyenne par client",
            [("sum by (application, clientName) (rate(feign_client_requests_seconds_sum[1m])) / sum by (application, clientName) (rate(feign_client_requests_seconds_count[1m]))",
              "{{application}} → {{clientName}}")], unite="s")
r4j.ajouter("timeseries", "RateLimiter : autorisations disponibles (5 / s)",
            [("min by (application, name) (resilience4j_ratelimiter_available_permissions)", "{{application}} → {{name}}")])

# ------------------------------------------------------------------ 3. Métier (mon module)
met = Tableau("events-metier", "Events — Métier (statistics-service)",
              "Indicateurs métier exposés par mon module (projections CQRS de db_stats) et répartition de charge")
met.ajouter("stat", "Événements", [("max(fst_events_count)", "")], 8, 5)
met.ajouter("stat", "Participants", [("max(fst_participants_count)", "")], 8, 5)
met.ajouter("stat", "Coût de la logistique réservée (DT)", [("max(fst_logistics_reserved_cost)", "")], 8, 5)
met.ajouter("timeseries", "Événements et participants au fil du temps",
            [("max(fst_events_count)", "événements"), ("max(fst_participants_count)", "participants")])
met.ajouter("timeseries", "Coût de la logistique réservée (DT)", [("max(fst_logistics_reserved_cost)", "coût réservé")])
met.ajouter("timeseries", "Répartition de charge : requêtes / s par instance de statistics-service",
            [(f'sum by (instance) (rate(http_server_requests_seconds_count{{application="statistics-service",{HORS_ACTUATOR}}}[1m]))', "{{instance}}")],
            unite="reqps")
met.ajouter("timeseries", "Répartition de charge : requêtes / s par instance de participant-service",
            [(f'sum by (instance) (rate(http_server_requests_seconds_count{{application="participant-service",{HORS_ACTUATOR}}}[1m]))', "{{instance}}")],
            unite="reqps")
met.ajouter("timeseries", "Latence p95 des statistiques (APRÈS CQRS : lecture locale)",
            [(f'histogram_quantile(0.95, sum by (le, uri) (rate(http_server_requests_seconds_bucket{{application="statistics-service",{HORS_ACTUATOR}}}[1m])))', "{{uri}}")],
            24, 8, "s")

ICI.mkdir(parents=True, exist_ok=True)
for t in (jvm, r4j, met):
    (ICI / f"{t.uid}.json").write_text(json.dumps(t.json(), ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"{t.uid}.json : {len(t.panneaux)} panneaux")
