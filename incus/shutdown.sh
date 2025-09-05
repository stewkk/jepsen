#!/usr/bin/env sh

N=1

for ((i = 1; i <= N; i++)); do
  incus stop n${i};
  incus delete n${i};
done

resolvectl revert wlp0s20f3
