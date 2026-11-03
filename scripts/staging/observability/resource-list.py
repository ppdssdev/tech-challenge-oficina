#!/usr/bin/env python3
"""Normalize kubectl get --ignore-not-found output, never hide parse failures."""
import json
import sys


def resource_list(raw):
    # kubectl may emit zero bytes and exit 0 when every requested object is absent.
    # Whitespace, truncated JSON and other malformed output must still fail.
    obj = json.loads(raw) if raw != '' else {'apiVersion': 'v1', 'kind': 'List', 'items': []}
    if obj.get('kind') != 'List':
        obj = {'apiVersion': 'v1', 'kind': 'List', 'items': [obj]}
    for item in obj['items']:
        if item['metadata'].get('labels', {}).get('app.kubernetes.io/part-of') != 'oficina-observability':
            raise ValueError('Nome já utilizado por recurso não gerenciado: ' + item['metadata']['name'])
    return obj


if __name__ == '__main__':
    with open(sys.argv[1]) as source:
        print(json.dumps(resource_list(source.read())))
