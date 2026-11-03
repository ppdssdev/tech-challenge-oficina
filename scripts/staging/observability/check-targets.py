#!/usr/bin/env python3
"""Require one healthy Prometheus target for each expected Ready API pod."""
import json
import sys


def ready_pods(pods, expected):
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
    return ready


def validate(pods, response, expected=3):
    ready = ready_pods(pods, expected)
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


def api_sample(pods, expected, deployment, after, deployment_after):
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
    names = ready_pods(pods, count)
    if names != ready_pods(after, count):
        raise ValueError('Pods mudaram durante a amostra')

    def identities(items):
        return sorted((p['metadata']['name'], p['metadata']['uid'], p['status']['podIP'],
                       sorted((c['name'], c['restartCount'])
                              for c in p['status'].get('containerStatuses', [])))
                      for p in items['items'] if p['metadata']['name'] in names)

    if identities(pods) != identities(after):
        raise ValueError('Pods mudaram durante a amostra')
    return {'deployment': state, 'pods': identities(pods)}


def stable_sample(pods, response, expected, deployment, after, deployment_after):
    sample = api_sample(pods, expected, deployment, after, deployment_after)
    count = sample['deployment'][2]
    validate(pods, response, count)
    validate(after, response, count)
    return 'Targets UP individuais (15s), amostra estável: ' + json.dumps(sample, sort_keys=True)


if __name__ == '__main__':
    try:
        if sys.argv[1] == '--api-only':
            def read(path):
                with open(path) as f:
                    return json.load(f)
            print(json.dumps(api_sample(read(sys.argv[2]), sys.argv[3], read(sys.argv[4]),
                                        read(sys.argv[5]), read(sys.argv[6])), sort_keys=True))
            sys.exit(0)
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
