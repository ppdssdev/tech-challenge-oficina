#!/usr/bin/env python3
"""Select only the incremental observability update from the rendered overlay."""
import json
import sys
import yaml


def select(objects):
    allowed = {
        ('Deployment', 'oficina-observability-prometheus'),
        ('Deployment', 'oficina-observability-grafana'),
        ('Deployment', 'oficina-observability-tempo'),
        ('Service', 'oficina-tempo'),
        ('PersistentVolumeClaim', 'oficina-observability-tempo'),
        ('NetworkPolicy', 'oficina-observability-tempo'),
    }
    prefixes = ('oficina-observability-prometheus-', 'oficina-observability-grafana-',
                'oficina-observability-dashboard-', 'oficina-observability-tempo-')
    return {'apiVersion': 'v1', 'kind': 'List', 'items': [obj for obj in objects
            if (obj['kind'], obj['metadata']['name']) in allowed
            or (obj['kind'] == 'ConfigMap' and obj['metadata']['name'].startswith(prefixes))]}


if __name__ == '__main__':
    print(json.dumps(select(yaml.safe_load_all(sys.stdin))))
