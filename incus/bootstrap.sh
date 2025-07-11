#!/usr/bin/env sh

# for i in {1..10}; do incus launch images:debian/12 n${i}; done

# for i in {1..10}; do
#   incus exec n${i} -- sh -c "apt-get -qy update && apt-get -qy install openssh-server sudo";
# done

# for i in {1..10}; do
#   incus exec n${i} -- sh -c "mkdir -p /root/.ssh && chmod 700 /root/.ssh/";
#   incus file push ~/.ssh/id_ed25519.pub n${i}/root/.ssh/authorized_keys --uid 0 --gid 0 --mode 644;
# done

# for i in {1..10}; do
#   incus exec n${i} -- bash -c 'echo -e "root\nroot\n" | passwd root';
#   incus exec n${i} -- sed -i 's,^#\?PermitRootLogin .*,PermitRootLogin yes,g' /etc/ssh/sshd_config;
#   incus exec n${i} -- systemctl restart sshd;
# done

for n in {1..10}; do
  ssh-keyscan -t ed25519 n${n}.incus >> ~/.ssh/known_hosts;
done
