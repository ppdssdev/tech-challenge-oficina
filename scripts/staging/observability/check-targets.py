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
    if expected < 1 or len(ready) != expected or len(set(ready.values())) != expected:
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


def stable_sample(pods, response, expected, deployment, after, deployment_after):
    """Use desired replicas, never the observed target count, as the acceptance floor."""
    def deployment_state(obj):
        meta, spec, status = obj['metadata'], obj['spec'], obj['status']
        replicas = spec['replicas']
        if (replicas < 1 or status.get('observedGeneration', 0) < meta['generation']
                or any(status.get(key, 0) != replicas for key in
                       ('replicas', 'updatedReplicas', 'readyReplicas', 'availableReplicas'))):
            raise ValueError('Deployment oficina-api ainda não convergiu; aguardando HPA/rollout')
        return meta['uid'], meta['generation'], replicas

    state = deployment_state(deployment)
    if state != deployment_state(deployment_after):
        raise ValueError('Deployment mudou durante a amostra')
    count = state[2] if expected == 'auto' else int(expected)
    if count != state[2]:
        raise ValueError('EXPECTED_API_PODS difere das réplicas desejadas pelo Deployment/HPA')
    names = validate(pods, response, count)
    validate(after, response, count)

    def identities(items):
        return sorted((p['metadata']['name'], p['metadata']['uid'], p['status']['podIP'])
                      for p in items['items'] if p['metadata']['name'] in names)

    if identities(pods) != identities(after):
        raise ValueError('Pods mudaram durante a amostra')
    return 'Targets UP individuais (15s), amostra estável: ' + json.dumps(
        {'deployment': state, 'pods': identities(pods)}, sort_keys=True)


if __name__ == '__main__':
    try:
        with open(sys.argv[1]) as f:
            pods = json.load(f)
        with open(sys.argv[2]) as f:
            targets = json.load(f)
        with open(sys.argv[4]) as f:
            deployment = json.load(f)
        with open(sys.argv[5]) as f:
            after = json.load(f)
        with open(sys.argv[6]) as f:
            deployment_after = json.load(f)
        print(stable_sample(pods, targets, sys.argv[3], deployment, after, deployment_after))
    except (ValueError, KeyError) as error:
        sys.exit(str(error))
