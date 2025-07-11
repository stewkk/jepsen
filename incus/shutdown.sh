#!/usr/bin/env sh

for i in {1..10}; do
  incus stop n${i};
  incus delete n${i};
done

resolvectl revert wlp0s20f3
