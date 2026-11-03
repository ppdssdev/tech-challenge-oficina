#!/usr/bin/env python3
"""Require one healthy Prometheus target for each expected Ready API pod."""
import json
import sys


def validate(pods, response, expected=3):
    ready = {
        p['metadata']['name']: p['status']['podIP']
        for p in pods['items']
        if p.get('status', {}).get('phase') == 'Running'
        and not p['metadata'].get('deletionTimestamp')
        and any(c['type'] == 'Ready' and c['status'] == 'True'
                for c in p.get('status', {}).get('conditions', []))
    }
    if len(ready) != expected or len(set(ready.values())) != expected:
        raise ValueError(f'Esperados {expected} pods Ready com IPs distintos; encontrados {len(ready)}')
    if response.get('status') != 'success':
        raise ValueError('Consulta de targets falhou')
    targets = [t for t in response['data']['activeTargets']
               if t.get('labels', {}).get('job') == 'oficina-api']
    if len(targets) != expected:
        raise ValueError(f'Esperados {expected} targets; encontrados {len(targets)}')
    observed = set()
    for target in targets:
        labels = target['labels']
        pod = labels.get('pod')
        if (pod not in ready or pod in observed
                or labels.get('namespace') != 'oficina-staging'
                or target.get('health') != 'up'
                or target.get('lastError')
                or target.get('scrapeInterval') != '15s'
                or target.get('scrapeUrl') != f'http://{ready[pod]}:8080/actuator/prometheus'):
            raise ValueError('Target ausente, duplicado, não saudável ou diferente do pod: ' + str(pod))
        observed.add(pod)
    return observed


if __name__ == '__main__':
    try:
        with open(sys.argv[1]) as f:
            pods = json.load(f)
        with open(sys.argv[2]) as f:
            targets = json.load(f)
        names = validate(pods, targets, int(sys.argv[3]))
        print('Targets UP individuais (15s): ' + ', '.join(sorted(names)))
    except (ValueError, KeyError) as error:
        sys.exit(str(error))
