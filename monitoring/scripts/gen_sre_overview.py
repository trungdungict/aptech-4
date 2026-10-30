#!/usr/bin/env python3
"""Generate aptech4-sre-overview.json — run from repo; do not provision this script."""
from __future__ import annotations

import json
from pathlib import Path

OUT = Path(__file__).resolve().parents[1] / "grafana" / "dashboards" / "aptech4-sre-overview.json"
DS = "${datasource}"
API = 'application="aptech4-api"'
API_URI = f'{API},uri=~"/api/v1/.*"'
RANGE = "$__range"


def T(expr: str, legend: str = "", ref: str = "A", *, instant: bool = False, heatmap: bool = False) -> dict:
    t: dict = {
        "refId": ref,
        "expr": expr,
        "legendFormat": legend,
        "datasource": {"type": "prometheus", "uid": DS},
        "editorMode": "code",
        "range": not instant,
        "instant": instant,
    }
    if heatmap:
        t["format"] = "heatmap"
        t["legendFormat"] = "{{le}}"
    return t


def thr(steps: list) -> dict:
    return {"mode": "absolute", "steps": steps}


def P(
    pid: int,
    title: str,
    ptype: str,
    x: int,
    y: int,
    w: int,
    h: int,
    *,
    desc: str = "",
    targets: list | None = None,
    field: dict | None = None,
    options: dict | None = None,
) -> dict:
    return {
        "id": pid,
        "type": ptype,
        "title": title,
        "description": desc,
        "gridPos": {"h": h, "w": w, "x": x, "y": y},
        "datasource": {"type": "prometheus", "uid": DS},
        "targets": targets or [],
        "fieldConfig": {"defaults": field or {"noValue": "N/A"}, "overrides": []},
        "options": options or {},
    }


def row(pid: int, title: str, y: int) -> dict:
    return {
        "id": pid,
        "type": "row",
        "title": title,
        "gridPos": {"h": 1, "w": 24, "x": 0, "y": y},
        "collapsed": False,
        "panels": [],
    }


def err_rate(status_re: str) -> str:
    """4xx/5xx rate; measured 0 when API traffic exists but no matching status series."""
    specific = f'sum(rate(http_server_requests_seconds_count{{{API_URI},status=~"{status_re}"}}[5m]))'
    all_api = f'sum(rate(http_server_requests_seconds_count{{{API_URI}}}[5m]))'
    return f"({specific}) or (({all_api}) * 0)"


panels: list[dict] = []
y = 0

# ----- ROW 1 -----
panels.append(row(100, "ROW 1 — SYSTEM HEALTH", y))
y += 1

panels.append(
    P(
        1,
        "Overall System Health",
        "stat",
        0,
        y,
        5,
        4,
        desc=(
            "Mandatory: API (aptech4-api), PostgreSQL exporter, Redis exporter. "
            "avg(up) → 1 HEALTHY, (0,1) DEGRADED, 0 UNAVAILABLE. Ollama is optional and excluded."
        ),
        targets=[T('avg(up{job=~"aptech4-api|postgres-exporter|redis-exporter"})', "mandatory", instant=True)],
        field={
            "unit": "percentunit",
            "min": 0,
            "max": 1,
            "noValue": "N/A",
            "thresholds": thr(
                [
                    {"color": "red", "value": None},
                    {"color": "orange", "value": 0.34},
                    {"color": "yellow", "value": 0.67},
                    {"color": "green", "value": 1},
                ]
            ),
            "mappings": [
                {"type": "range", "options": {"from": 1, "to": 1, "result": {"text": "HEALTHY", "color": "green"}}},
                {
                    "type": "range",
                    "options": {"from": 0.01, "to": 0.99, "result": {"text": "DEGRADED", "color": "orange"}},
                },
                {"type": "range", "options": {"from": 0, "to": 0, "result": {"text": "UNAVAILABLE", "color": "red"}}},
            ],
        },
        options={
            "reduceOptions": {"calcs": ["lastNotNull"]},
            "colorMode": "background",
            "graphMode": "none",
            "textMode": "auto",
        },
    )
)

panels.append(
    P(
        2,
        "API Availability",
        "gauge",
        5,
        y,
        4,
        4,
        desc="up{job=aptech4-api}. Scrape availability — not HTTP success rate.",
        targets=[T('up{job="aptech4-api"}', "api", instant=True)],
        field={
            "unit": "percentunit",
            "min": 0,
            "max": 1,
            "noValue": "N/A",
            "thresholds": thr(
                [
                    {"color": "red", "value": None},
                    {"color": "green", "value": 1},
                ]
            ),
        },
        options={"reduceOptions": {"calcs": ["lastNotNull"]}, "showThresholdMarkers": True},
    )
)

panels.append(
    P(
        3,
        "API Request Rate",
        "stat",
        9,
        y,
        5,
        4,
        desc="rate([1m]) of /api/v1/* only (excludes actuator). Rate ≠ total count.",
        targets=[T(f'sum(rate(http_server_requests_seconds_count{{{API_URI}}}[1m]))', "req/s", instant=True)],
        field={
            "unit": "reqps",
            "decimals": 3,
            "noValue": "N/A",
            "thresholds": thr([{"color": "blue", "value": None}]),
        },
        options={
            "reduceOptions": {"calcs": ["lastNotNull"]},
            "colorMode": "value",
            "graphMode": "area",
            "textMode": "value_and_name",
        },
    )
)

panels.append(
    P(
        4,
        "HTTP Success Rate",
        "gauge",
        14,
        y,
        5,
        4,
        desc="2xx / all for /api/v1/* over 5m. Success rate ≠ availability.",
        targets=[
            T(
                f'sum(rate(http_server_requests_seconds_count{{{API_URI},status=~"2.."}}[5m])) '
                f'/ clamp_min(sum(rate(http_server_requests_seconds_count{{{API_URI}}}[5m])), 1e-9)',
                "2xx",
                instant=True,
            )
        ],
        field={
            "unit": "percentunit",
            "min": 0,
            "max": 1,
            "noValue": "N/A",
            "decimals": 3,
            "thresholds": thr(
                [
                    {"color": "red", "value": None},
                    {"color": "yellow", "value": 0.95},
                    {"color": "green", "value": 0.99},
                ]
            ),
        },
        options={"reduceOptions": {"calcs": ["lastNotNull"]}, "showThresholdMarkers": True},
    )
)

panels.append(
    P(
        5,
        "API p95 Latency",
        "stat",
        19,
        y,
        5,
        4,
        desc="histogram_quantile(0.95) over buckets for /api/v1/* — global, not avg of per-route p95.",
        targets=[
            T(
                f'histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{{{API_URI}}}[5m])))',
                "p95",
                instant=True,
            )
        ],
        field={
            "unit": "s",
            "decimals": 3,
            "noValue": "N/A",
            "thresholds": thr(
                [
                    {"color": "green", "value": None},
                    {"color": "yellow", "value": 0.5},
                    {"color": "red", "value": 2},
                ]
            ),
        },
        options={"reduceOptions": {"calcs": ["lastNotNull"]}, "colorMode": "value", "graphMode": "area"},
    )
)
y += 4

# ----- ROW 2 -----
panels.append(row(200, "ROW 2 — TRAFFIC & REQUEST VOLUME", y))
y += 1
y2 = y

panels.append(
    P(
        7,
        "API Request Rate",
        "timeseries",
        0,
        y2,
        12,
        8,
        desc="rate([1m]) for /api/v1/*. Actuator excluded.",
        targets=[
            T(f'sum(rate(http_server_requests_seconds_count{{{API_URI}}}[1m]))', "all"),
            T(f'sum(rate(http_server_requests_seconds_count{{{API_URI},status=~"2.."}}[1m]))', "2xx", "B"),
            T(f'sum(rate(http_server_requests_seconds_count{{{API_URI},status=~"4.."}}[1m]))', "4xx", "C"),
            T(f'sum(rate(http_server_requests_seconds_count{{{API_URI},status=~"5.."}}[1m]))', "5xx", "D"),
        ],
        field={
            "unit": "reqps",
            "noValue": "N/A",
            "custom": {"drawStyle": "line", "fillOpacity": 12, "showPoints": "never", "spanNulls": False},
        },
        options={"legend": {"displayMode": "list", "placement": "bottom", "showLegend": True}},
    )
)

panels.append(
    P(
        8,
        "Total API Requests",
        "stat",
        12,
        y2,
        4,
        4,
        desc="increase() over Grafana $__range for /api/v1/*.",
        targets=[
            T(
                f'sum(increase(http_server_requests_seconds_count{{{API_URI}}}[{RANGE}]))',
                "total",
                instant=True,
            )
        ],
        field={"unit": "short", "decimals": 0, "noValue": "N/A"},
        options={"reduceOptions": {"calcs": ["lastNotNull"]}, "colorMode": "value", "graphMode": "none"},
    )
)

panels.append(
    P(
        9,
        "HTTP Status Groups ($__range)",
        "stat",
        16,
        y2,
        8,
        4,
        desc="increase() by class. Order checkout verified as HTTP 201.",
        targets=[
            T(f'sum(increase(http_server_requests_seconds_count{{{API_URI},status=~"2.."}}[{RANGE}]))', "2xx", instant=True),
            T(
                f'sum(increase(http_server_requests_seconds_count{{{API_URI},status=~"3.."}}[{RANGE}]))',
                "3xx",
                "B",
                instant=True,
            ),
            T(
                f'sum(increase(http_server_requests_seconds_count{{{API_URI},status=~"4.."}}[{RANGE}]))',
                "4xx",
                "C",
                instant=True,
            ),
            T(
                f'sum(increase(http_server_requests_seconds_count{{{API_URI},status=~"5.."}}[{RANGE}]))',
                "5xx",
                "D",
                instant=True,
            ),
        ],
        field={"unit": "short", "decimals": 0, "noValue": "N/A"},
        options={
            "reduceOptions": {"calcs": ["lastNotNull"]},
            "colorMode": "background",
            "graphMode": "none",
            "textMode": "value_and_name",
            "orientation": "horizontal",
        },
    )
)

panels.append(
    P(
        10,
        "HTTP Status Distribution",
        "piechart",
        12,
        y2 + 4,
        6,
        8,
        desc="Pie of increase() by exact status over $__range.",
        targets=[
            T(
                f'sum by (status) (increase(http_server_requests_seconds_count{{{API_URI}}}[{RANGE}]))',
                "{{status}}",
                instant=True,
            )
        ],
        field={"noValue": "N/A"},
        options={
            "reduceOptions": {"calcs": ["lastNotNull"]},
            "legend": {"displayMode": "table", "placement": "right", "showLegend": True, "values": ["value", "percent"]},
            "pieType": "pie",
            "displayLabels": ["name"],
        },
    )
)

panels.append(
    P(
        11,
        "HTTP Status Counts",
        "barchart",
        18,
        y2 + 4,
        6,
        8,
        desc="Bar of increase() by status over $__range (200, 201, 401, … when present).",
        targets=[
            T(
                f'sum by (status) (increase(http_server_requests_seconds_count{{{API_URI}}}[{RANGE}]))',
                "{{status}}",
                instant=True,
            )
        ],
        field={"unit": "short", "noValue": "N/A"},
        options={"legend": {"displayMode": "list", "placement": "bottom", "showLegend": False}},
    )
)

panels.append(
    P(
        12,
        "Top API Endpoints by Request Volume",
        "bargauge",
        0,
        y2 + 8,
        12,
        8,
        desc="topk by increase($__range) on normalized uri templates (/api/v1/...).",
        targets=[
            T(
                f'topk(10, sum by (uri) (increase(http_server_requests_seconds_count{{{API_URI}}}[{RANGE}])))',
                "{{uri}}",
                instant=True,
            )
        ],
        field={
            "unit": "short",
            "noValue": "N/A",
            "thresholds": thr([{"color": "blue", "value": None}, {"color": "purple", "value": 100}]),
        },
        options={
            "reduceOptions": {"calcs": ["lastNotNull"]},
            "displayMode": "gradient",
            "orientation": "horizontal",
            "showUnfilled": True,
        },
    )
)
y = y2 + 16

# ----- ROW 3 -----
panels.append(row(300, "ROW 3 — LATENCY & ERRORS", y))
y += 1
y3 = y

panels.append(
    P(
        13,
        "API Latency Percentiles",
        "timeseries",
        0,
        y3,
        12,
        8,
        desc="p50/p95/p99 via histogram_quantile on http_server_requests_seconds_bucket (/api/v1/*).",
        targets=[
            T(
                f'histogram_quantile(0.50, sum by (le) (rate(http_server_requests_seconds_bucket{{{API_URI}}}[5m])))',
                "p50",
            ),
            T(
                f'histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{{{API_URI}}}[5m])))',
                "p95",
                "B",
            ),
            T(
                f'histogram_quantile(0.99, sum by (le) (rate(http_server_requests_seconds_bucket{{{API_URI}}}[5m])))',
                "p99",
                "C",
            ),
        ],
        field={"unit": "s", "noValue": "N/A", "custom": {"drawStyle": "line", "fillOpacity": 8, "showPoints": "never"}},
        options={"legend": {"displayMode": "list", "placement": "bottom", "showLegend": True}},
    )
)

panels.append(
    P(
        14,
        "HTTP Error Rate",
        "timeseries",
        12,
        y3,
        12,
        8,
        desc="4xx and 5xx req/s. Shows 0 when API traffic exists but no errors; N/A if no API series.",
        targets=[
            T(err_rate("4.."), "4xx/s"),
            T(err_rate("5.."), "5xx/s", "B"),
        ],
        field={"unit": "reqps", "noValue": "N/A", "custom": {"drawStyle": "line", "fillOpacity": 15, "showPoints": "never"}},
        options={"legend": {"displayMode": "list", "placement": "bottom", "showLegend": True}},
    )
)

panels.append(
    P(
        15,
        "HTTP Error Distribution",
        "barchart",
        0,
        y3 + 8,
        8,
        8,
        desc="increase() of 4xx/5xx statuses over $__range.",
        targets=[
            T(
                f'sum by (status) (increase(http_server_requests_seconds_count{{{API_URI},status=~"[45].."}}[{RANGE}]))',
                "{{status}}",
                instant=True,
            )
        ],
        field={"unit": "short", "noValue": "N/A"},
        options={"legend": {"displayMode": "list", "placement": "bottom", "showLegend": False}},
    )
)

panels.append(
    P(
        16,
        "Request Latency Distribution",
        "heatmap",
        8,
        y3 + 8,
        8,
        8,
        desc="Heatmap from real histogram buckets (rate of *_bucket). N/A if buckets missing.",
        targets=[
            T(
                f'sum by (le) (rate(http_server_requests_seconds_bucket{{{API_URI}}}[5m]))',
                "{{le}}",
                heatmap=True,
            )
        ],
        field={"unit": "s", "noValue": "N/A", "custom": {"scaleDistribution": {"type": "linear"}}},
        options={
            "calculate": False,
            "cellGap": 1,
            "color": {"scheme": "Spectral", "steps": 64},
            "yAxis": {"unit": "s", "decimals": 3},
            "legend": {"show": True},
        },
    )
)

panels.append(
    P(
        17,
        "Slowest API Endpoints",
        "bargauge",
        16,
        y3 + 8,
        8,
        8,
        desc="Mean latency ≈ rate(sum)/rate(count) by uri over 5m; topk 8.",
        targets=[
            T(
                f'topk(8, sum by (uri) (rate(http_server_requests_seconds_sum{{{API_URI}}}[5m])) '
                f'/ clamp_min(sum by (uri) (rate(http_server_requests_seconds_count{{{API_URI}}}[5m])), 1e-9))',
                "{{uri}}",
                instant=True,
            )
        ],
        field={
            "unit": "s",
            "noValue": "N/A",
            "thresholds": thr(
                [
                    {"color": "green", "value": None},
                    {"color": "yellow", "value": 0.2},
                    {"color": "red", "value": 1},
                ]
            ),
        },
        options={
            "reduceOptions": {"calcs": ["lastNotNull"]},
            "displayMode": "gradient",
            "orientation": "horizontal",
        },
    )
)
y = y3 + 16

# ----- ROW 4 -----
panels.append(row(400, "ROW 4 — INFRASTRUCTURE & JVM", y))
y += 1
y4 = y

panels.append(
    P(
        18,
        "API Process CPU",
        "gauge",
        0,
        y4,
        4,
        4,
        desc="process_cpu_usage for aptech4-api JVM process (0–1 of a core ratio from Micrometer).",
        targets=[T(f'process_cpu_usage{{{API}}}', "api process", instant=True)],
        field={**{"unit": "percentunit", "min": 0, "max": 1, "noValue": "N/A"}, **{
            "thresholds": thr(
                [
                    {"color": "green", "value": None},
                    {"color": "yellow", "value": 0.5},
                    {"color": "red", "value": 0.85},
                ]
            )
        }},
        options={"reduceOptions": {"calcs": ["lastNotNull"]}, "showThresholdMarkers": True},
    )
)

panels.append(
    P(
        19,
        "JVM Heap Usage",
        "gauge",
        4,
        y4,
        4,
        4,
        desc="sum(used)/sum(max) for area=heap. Not container RSS.",
        targets=[
            T(
                f'sum(jvm_memory_used_bytes{{{API},area="heap"}}) / clamp_min(sum(jvm_memory_max_bytes{{{API},area="heap"}}), 1)',
                "heap",
                instant=True,
            )
        ],
        field={
            "unit": "percentunit",
            "min": 0,
            "max": 1,
            "noValue": "N/A",
            "thresholds": thr(
                [
                    {"color": "green", "value": None},
                    {"color": "yellow", "value": 0.7},
                    {"color": "red", "value": 0.9},
                ]
            ),
        },
        options={"reduceOptions": {"calcs": ["lastNotNull"]}, "showThresholdMarkers": True},
    )
)

panels.append(
    P(
        20,
        "Docker VM CPU (cAdvisor id=/)",
        "gauge",
        8,
        y4,
        4,
        4,
        desc=(
            "cAdvisor root cgroup CPU on Docker Desktop macOS (VM aggregate). "
            "NOT per-container API CPU. Named containers appear on Linux when labels exist."
        ),
        targets=[T('sum(rate(container_cpu_usage_seconds_total{job="cadvisor",id="/"}[5m]))', "vm cores", instant=True)],
        field={
            "unit": "cores",
            "noValue": "N/A",
            "thresholds": thr(
                [
                    {"color": "green", "value": None},
                    {"color": "yellow", "value": 2},
                    {"color": "red", "value": 4},
                ]
            ),
        },
        options={"reduceOptions": {"calcs": ["lastNotNull"]}, "showThresholdMarkers": True},
    )
)

panels.append(
    P(
        21,
        "Docker VM Memory (cAdvisor id=/)",
        "stat",
        12,
        y4,
        4,
        4,
        desc="container_memory_working_set_bytes{id=/} — Docker Desktop VM aggregate, not JVM heap.",
        targets=[T('container_memory_working_set_bytes{job="cadvisor",id="/"}', "vm rss", instant=True)],
        field={"unit": "bytes", "noValue": "N/A"},
        options={"reduceOptions": {"calcs": ["lastNotNull"]}, "colorMode": "value", "graphMode": "area"},
    )
)

panels.append(
    P(
        22,
        "CPU Usage Trend",
        "timeseries",
        16,
        y4,
        8,
        8,
        desc="API process_cpu_usage vs Docker VM cAdvisor root CPU (cores).",
        targets=[
            T(f'process_cpu_usage{{{API}}}', "api process (ratio)"),
            T('sum(rate(container_cpu_usage_seconds_total{job="cadvisor",id="/"}[5m]))', "docker-vm cores", "B"),
            T(
                'sum by (name) (rate(container_cpu_usage_seconds_total{name=~".+"}[5m]))',
                "container {{name}}",
                "C",
            ),
        ],
        field={"noValue": "N/A", "custom": {"drawStyle": "line", "fillOpacity": 8, "showPoints": "never"}},
        options={"legend": {"displayMode": "list", "placement": "bottom", "showLegend": True}},
    )
)

panels.append(
    P(
        23,
        "Memory Usage Trend",
        "timeseries",
        0,
        y4 + 4,
        8,
        8,
        desc="JVM heap used vs Docker VM working set. Different scopes — do not compare as equals.",
        targets=[
            T(f'sum(jvm_memory_used_bytes{{{API},area="heap"}})', "jvm heap used"),
            T('container_memory_working_set_bytes{job="cadvisor",id="/"}', "docker-vm working set", "B"),
            T('sum by (name) (container_memory_working_set_bytes{name=~".+"})', "container {{name}}", "C"),
        ],
        field={"unit": "bytes", "noValue": "N/A", "custom": {"drawStyle": "line", "fillOpacity": 8, "showPoints": "never"}},
        options={"legend": {"displayMode": "list", "placement": "bottom", "showLegend": True}},
    )
)

panels.append(
    P(
        24,
        "Container / VM Resources",
        "bargauge",
        8,
        y4 + 4,
        8,
        8,
        desc="VM memory + API heap when present; named containers when cAdvisor exposes name labels (Linux).",
        targets=[
            T('container_memory_working_set_bytes{job="cadvisor",id="/"}', "docker-vm mem", instant=True),
            T(f'sum(jvm_memory_used_bytes{{{API},area="heap"}})', "api heap used", "B", instant=True),
            T('sum by (name) (container_memory_working_set_bytes{name=~".+"})', "{{name}}", "C", instant=True),
        ],
        field={"unit": "bytes", "noValue": "N/A"},
        options={
            "reduceOptions": {"calcs": ["lastNotNull"]},
            "displayMode": "gradient",
            "orientation": "horizontal",
        },
    )
)

panels.append(
    P(
        25,
        "JVM GC Pause",
        "timeseries",
        0,
        y4 + 12,
        12,
        7,
        desc="rate(jvm_gc_pause_seconds_sum[5m]) — seconds of pause per second.",
        targets=[T(f'sum(rate(jvm_gc_pause_seconds_sum{{{API}}}[5m]))', "gc pause s/s")],
        field={"unit": "s", "noValue": "N/A", "custom": {"drawStyle": "line", "fillOpacity": 20, "showPoints": "never"}},
        options={"legend": {"displayMode": "list", "placement": "bottom", "showLegend": True}},
    )
)

panels.append(
    P(
        26,
        "JVM Live Threads",
        "timeseries",
        12,
        y4 + 12,
        12,
        7,
        desc="jvm_threads_live_threads for aptech4-api.",
        targets=[T(f'jvm_threads_live_threads{{{API}}}', "live threads")],
        field={"unit": "short", "noValue": "N/A", "custom": {"drawStyle": "line", "fillOpacity": 10, "showPoints": "never"}},
        options={"legend": {"displayMode": "list", "placement": "bottom", "showLegend": True}},
    )
)
y = y4 + 19

# ----- ROW 5 -----
panels.append(row(500, "ROW 5 — DATABASE & DEPENDENCIES", y))
y += 1
y5 = y

panels.append(
    P(
        27,
        "PostgreSQL Health",
        "stat",
        0,
        y5,
        3,
        4,
        desc="pg_up from postgres-exporter (server reachable).",
        targets=[T("pg_up", "pg", instant=True)],
        field={
            "mappings": [
                {"type": "value", "options": {"0": {"text": "DOWN", "color": "red"}, "1": {"text": "UP", "color": "green"}}},
            ],
            "noValue": "N/A",
        },
        options={"reduceOptions": {"calcs": ["lastNotNull"]}, "colorMode": "background", "graphMode": "none", "textMode": "auto"},
    )
)

panels.append(
    P(
        28,
        "Redis Health",
        "stat",
        3,
        y5,
        3,
        4,
        desc="redis_up from redis-exporter.",
        targets=[T("redis_up", "redis", instant=True)],
        field={
            "mappings": [
                {"type": "value", "options": {"0": {"text": "DOWN", "color": "red"}, "1": {"text": "UP", "color": "green"}}},
            ],
            "noValue": "N/A",
        },
        options={"reduceOptions": {"calcs": ["lastNotNull"]}, "colorMode": "background", "graphMode": "none", "textMode": "auto"},
    )
)

panels.append(
    P(
        29,
        "Ollama Health",
        "stat",
        6,
        y5,
        3,
        4,
        desc=(
            "Optional. No Prometheus scrape job for Ollama in this stack → N/A until instrumented. "
            "Down/missing must not fail Overall System Health."
        ),
        targets=[T('up{job="ollama"}', "ollama", instant=True)],
        field={"noValue": "N/A (not scraped)", "mappings": [
            {"type": "value", "options": {"0": {"text": "DOWN", "color": "orange"}, "1": {"text": "UP", "color": "green"}}},
        ]},
        options={"reduceOptions": {"calcs": ["lastNotNull"]}, "colorMode": "background", "graphMode": "none"},
    )
)

panels.append(
    P(
        30,
        "Hikari Pool Usage",
        "gauge",
        9,
        y5,
        5,
        4,
        desc="hikaricp_connections_active / hikaricp_connections_max (API pool — not Postgres server-wide).",
        targets=[
            T(
                f'hikaricp_connections_active{{{API}}} / clamp_min(hikaricp_connections_max{{{API}}}, 1)',
                "hikari",
                instant=True,
            )
        ],
        field={
            "unit": "percentunit",
            "min": 0,
            "max": 1,
            "noValue": "N/A",
            "thresholds": thr(
                [
                    {"color": "green", "value": None},
                    {"color": "yellow", "value": 0.7},
                    {"color": "red", "value": 0.9},
                ]
            ),
        },
        options={"reduceOptions": {"calcs": ["lastNotNull"]}, "showThresholdMarkers": True},
    )
)

panels.append(
    P(
        31,
        "PostgreSQL Connection Utilization",
        "gauge",
        14,
        y5,
        5,
        4,
        desc="aptech4 backends / pg_settings_max_connections (verified max available from exporter).",
        targets=[
            T(
                'pg_stat_database_numbackends{datname="aptech4"} / on() group_left() pg_settings_max_connections',
                "pg util",
                instant=True,
            )
        ],
        field={
            "unit": "percentunit",
            "min": 0,
            "max": 1,
            "noValue": "N/A",
            "thresholds": thr(
                [
                    {"color": "green", "value": None},
                    {"color": "yellow", "value": 0.5},
                    {"color": "red", "value": 0.8},
                ]
            ),
        },
        options={"reduceOptions": {"calcs": ["lastNotNull"]}, "showThresholdMarkers": True},
    )
)

panels.append(
    P(
        32,
        "Redis Memory Used",
        "stat",
        19,
        y5,
        5,
        4,
        desc="redis_memory_used_bytes absolute. Utilization % omitted when redis_memory_max_bytes is 0 (unlimited).",
        targets=[T("redis_memory_used_bytes", "used", instant=True)],
        field={"unit": "bytes", "noValue": "N/A"},
        options={"reduceOptions": {"calcs": ["lastNotNull"]}, "colorMode": "value", "graphMode": "area"},
    )
)

panels.append(
    P(
        33,
        "PostgreSQL Connections",
        "timeseries",
        0,
        y5 + 4,
        12,
        8,
        desc="pg_stat_database_numbackends{datname=aptech4} + Hikari active/idle (pool vs server).",
        targets=[
            T('pg_stat_database_numbackends{datname="aptech4"}', "pg backends"),
            T(f'hikaricp_connections_active{{{API}}}', "hikari active", "B"),
            T(f'hikaricp_connections_idle{{{API}}}', "hikari idle", "C"),
            T(f'hikaricp_connections_max{{{API}}}', "hikari max", "D"),
        ],
        field={"unit": "short", "noValue": "N/A", "custom": {"drawStyle": "line", "fillOpacity": 8, "showPoints": "never"}},
        options={"legend": {"displayMode": "list", "placement": "bottom", "showLegend": True}},
    )
)

panels.append(
    P(
        34,
        "Redis Clients & Commands",
        "timeseries",
        12,
        y5 + 4,
        12,
        8,
        desc="redis_connected_clients and rate(redis_commands_processed_total).",
        targets=[
            T("redis_connected_clients", "clients"),
            T("rate(redis_commands_processed_total[1m])", "commands/s", "B"),
        ],
        field={"noValue": "N/A", "custom": {"drawStyle": "line", "fillOpacity": 10, "showPoints": "never"}},
        options={"legend": {"displayMode": "list", "placement": "bottom", "showLegend": True}},
    )
)

panels.append(
    P(
        35,
        "Ollama / Chat (optional)",
        "timeseries",
        0,
        y5 + 12,
        24,
        7,
        desc=(
            "Chat: aptech_chat_requests_total rate by handler. "
            "Ollama container resources only if cAdvisor exposes name=~.*ollama.* (often absent on Docker Desktop Mac)."
        ),
        targets=[
            T('sum by (handler) (rate(aptech_chat_requests_total{application="aptech4-api"}[5m]))', "chat {{handler}}"),
            T('rate(container_cpu_usage_seconds_total{name=~".*ollama.*"}[5m])', "ollama cpu {{name}}", "B"),
            T('container_memory_working_set_bytes{name=~".*ollama.*"}', "ollama mem {{name}}", "C"),
        ],
        field={"noValue": "N/A", "custom": {"drawStyle": "line", "fillOpacity": 8, "showPoints": "never"}},
        options={"legend": {"displayMode": "list", "placement": "bottom", "showLegend": True}},
    )
)
y = y5 + 19

# ----- ROW 6 -----
panels.append(row(600, "ROW 6 — SLO READINESS", y))
y += 1
y6 = y

panels.append(
    P(
        36,
        "SLO Readiness",
        "stat",
        0,
        y6,
        8,
        5,
        desc=(
            "No formal SLO target is configured for this demo. "
            "Candidate SLIs documented in docs/MONITORING.md. Do not treat this as an error budget."
        ),
        targets=[T('aptech_slo_target_configured', "slo", instant=True)],
        field={
            "noValue": "Not configured",
            "mappings": [{"type": "special", "options": {"match": "null", "result": {"text": "Not configured", "color": "text"}}}],
        },
        options={"reduceOptions": {"calcs": ["lastNotNull"]}, "colorMode": "background", "graphMode": "none", "textMode": "auto"},
    )
)

panels.append(
    P(
        37,
        "Availability SLI (candidate)",
        "timeseries",
        8,
        y6,
        8,
        5,
        desc="Candidate SLI: up{job=aptech4-api}. Not bound to an SLO target.",
        targets=[T('up{job="aptech4-api"}', "api up")],
        field={"unit": "short", "min": 0, "max": 1, "noValue": "N/A", "custom": {"drawStyle": "line", "fillOpacity": 30}},
        options={"legend": {"displayMode": "list", "placement": "bottom", "showLegend": True}},
    )
)

panels.append(
    P(
        38,
        "Error Budget",
        "gauge",
        16,
        y6,
        8,
        5,
        desc="Not configured — no SLO target or error-budget metric. Shows N/A (does not fabricate a budget).",
        targets=[T("aptech_slo_error_budget_remaining", "budget", instant=True)],
        field={
            "unit": "percentunit",
            "min": 0,
            "max": 1,
            "noValue": "Not configured",
            "thresholds": thr([{"color": "text", "value": None}]),
        },
        options={"reduceOptions": {"calcs": ["lastNotNull"]}, "showThresholdMarkers": False},
    )
)

dashboard = {
    "uid": "aptech4-sre-overview",
    "title": "Aptech-4 | SRE System Overview",
    "description": (
        "Unified SRE overview for Aptech-4. One dashboard for Local (Compose) and Production "
        "(same JSON/UID). Mandatory deps: API, Postgres, Redis. Ollama optional. "
        "SLO targets not configured. See docs/MONITORING.md."
    ),
    "tags": ["aptech4", "sre", "overview"],
    "timezone": "browser",
    "schemaVersion": 39,
    "version": 1,
    "refresh": "30s",
    "time": {"from": "now-1h", "to": "now"},
    "editable": False,
    "graphTooltip": 1,
    "fiscalYearStartMonth": 0,
    "liveNow": False,
    "weekStart": "",
    "templating": {
        "list": [
            {
                "name": "datasource",
                "label": "Datasource",
                "type": "datasource",
                "query": "prometheus",
                "current": {"selected": True, "text": "Prometheus-Local", "value": "prometheus-local"},
                "regex": "/^Prometheus-/",
                "hide": 0,
                "includeAll": False,
                "multi": False,
                "refresh": 1,
                "skipUrlSync": False,
            },
            {
                "name": "environment",
                "label": "Environment",
                "type": "custom",
                "query": "Local,Production",
                "current": {"selected": True, "text": "Local", "value": "Local"},
                "options": [
                    {"selected": True, "text": "Local", "value": "Local"},
                    {"selected": False, "text": "Production", "value": "Production"},
                ],
                "hide": 0,
                "includeAll": False,
                "multi": False,
                "skipUrlSync": False,
            },
            {
                "name": "uri",
                "label": "Endpoint",
                "type": "query",
                "datasource": {"type": "prometheus", "uid": DS},
                "definition": f'label_values(http_server_requests_seconds_count{{{API},uri=~"/api/v1/.*"}}, uri)',
                "query": {
                    "query": f'label_values(http_server_requests_seconds_count{{{API},uri=~"/api/v1/.*"}}, uri)',
                    "refId": "StandardVariableQuery",
                },
                "refresh": 2,
                "includeAll": True,
                "allValue": ".*",
                "multi": True,
                "current": {"selected": True, "text": "All", "value": "$__all"},
                "hide": 0,
                "sort": 1,
            },
        ]
    },
    "annotations": {"list": []},
    "links": [],
    "panels": panels,
}

# Note: endpoint variable is informational for future filter wiring; core panels
# currently use fixed /api/v1/.* to keep demos stable. Documented in MONITORING.md.

# Use endpoint variable: allValue=/api/v1/.* keeps static assets out when All is selected.
API_URI_VAR = f'{API},uri=~"$uri"'


def rewrite_uri(expr: str) -> str:
    return expr.replace('uri=~"/api/v1/.*"', 'uri=~"$uri"')


for p in panels:
    for t in p.get("targets") or []:
        if "expr" in t:
            t["expr"] = rewrite_uri(t["expr"])

for v in dashboard["templating"]["list"]:
    if v.get("name") == "uri":
        v["allValue"] = "/api/v1/.*"

OUT.parent.mkdir(parents=True, exist_ok=True)
OUT.write_text(json.dumps(dashboard, indent=2) + "\n")
n_data = sum(1 for p in panels if p["type"] != "row")
n_rows = sum(1 for p in panels if p["type"] == "row")
print(f"Wrote {OUT} rows={n_rows} panels={n_data} total_objects={len(panels)}")
