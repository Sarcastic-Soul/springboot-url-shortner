#!/usr/bin/env python3
"""Fail if a rendered manifest sets pullPolicy: Never on an image nobody side-loads.

`kind load docker-image` puts the two images this repo builds onto the node, and the test
environments set image.pullPolicy=Never so a same-named stranger from Docker Hub cannot be
used instead. Anything else -- postgres, valkey -- is pulled from a registry. Inheriting
Never leaves it ErrImageNeverPull with no image on the node: the pod is created, the
container never starts, and `helm --wait` burns its entire timeout before saying so.

Reads a rendered manifest stream on stdin.
"""
import sys
import yaml

SIDE_LOADED = ("url-shortener-backend", "url-shortener-frontend")

POD_PATHS = (
    ("spec", "template", "spec"),                             # Deployment, StatefulSet, Job
    ("spec", "jobTemplate", "spec", "template", "spec"),      # CronJob
)


def pod_specs(doc):
    for path in POD_PATHS:
        node = doc
        for key in path:
            node = node.get(key) if isinstance(node, dict) else None
        if isinstance(node, dict):
            yield node


def main():
    offenders = []
    for doc in yaml.safe_load_all(sys.stdin):
        if not isinstance(doc, dict):
            continue
        name = doc.get("metadata", {}).get("name", "?")
        for pod in pod_specs(doc):
            containers = (pod.get("containers") or []) + (pod.get("initContainers") or [])
            for c in containers:
                image = c.get("image", "")
                if c.get("imagePullPolicy") == "Never" and not image.startswith(SIDE_LOADED):
                    offenders.append(f"{name}/{c.get('name', '?')} -> {image}")

    for o in offenders:
        print(f"::error::pullPolicy Never on an image that is not side-loaded: {o}")
    return 1 if offenders else 0


if __name__ == "__main__":
    sys.exit(main())
