#!/usr/bin/env sh

N=6

for ((i = 1; i <= N; i++)); do incus launch images:ubuntu/plucky n${i}; done

for ((i = 1; i <= N; i++)); do
  incus exec n${i} -- sh -c "apt-get -qy update && apt-get -qy install openssh-server sudo";
done

for ((i = 1; i <= N; i++)); do
  incus exec n${i} -- sh -c "mkdir -p /root/.ssh && chmod 700 /root/.ssh/";
  incus file push /home/st/.ssh/id_ed25519.pub n${i}/root/.ssh/authorized_keys --uid 0 --gid 0 --mode 644;
done

for ((i = 1; i <= N; i++)); do
  incus exec n${i} -- bash -c 'echo -e "root\nroot\n" | passwd root';
  incus exec n${i} -- sed -i 's,^#\?PermitRootLogin .*,PermitRootLogin yes,g' /etc/ssh/sshd_config;
  incus exec n${i} -- systemctl restart sshd;
done

resolvectl dns wlp0s20f3 10.0.100.1
resolvectl domain wlp0s20f3 ~incus

for ((i = 1; i <= N; i++)); do
  ssh-keyscan -t ed25519 n${i}.incus >> /home/st/.ssh/known_hosts;
done
