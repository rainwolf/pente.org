# dockerMail

Configuration for the `pente_mail` image (built from `../mail.Dockerfile`): a
Postfix relay with OpenDKIM signing.

This directory used to be copied wholesale into the image's `/etc`. It is now a
set of explicit, individually-copied files, so the image is reproducible from a
clone plus the two secrets listed below.

## Tracked files

| File | Copied to | What it is |
| --- | --- | --- |
| `postfix/main.cf` | `/etc/postfix/main.cf` | Postfix config. **Template** — contains `@MAIL_DOCKER_SUBNET@`. |
| `opendkim.conf` | `/etc/opendkim.conf` | OpenDKIM config. Milter listens on `inet:12301@localhost`. |
| `opendkim/KeyTable` | `/etc/opendkim/KeyTable` | Maps the `mail` selector to the private key path. |
| `opendkim/SigningTable` | `/etc/opendkim/SigningTable` | Signs everything `*@pente.org`. |
| `opendkim/TrustedHosts` | `/etc/opendkim/TrustedHosts` | Hosts signed for rather than verified. **Template** — contains `@MAIL_DOCKER_SUBNET@`. |
| `mailname` | `/etc/mailname` | `myorigin` source. |
| `start_dsg_mail.sh` | `/start_dsg_mail.sh` | Entrypoint: renders the templates, then starts syslogd, opendkim, postfix. |

The DNSSEC root trust anchor OpenDKIM's resolver uses (`/usr/share/dns/root.key`)
is no longer a file we ship: `mail.Dockerfile` installs the `dns-root-data`
package, which owns and updates it. A frozen copy would go stale at the next
root KSK (key-signing key) rollover; the package tracks Debian's own updates
instead. Rebuilding the image picks up whatever `dns-root-data` ships at that
point. OpenDKIM only reads the file once, at process start, so an already
running container keeps its old anchor until restarted onto the rebuilt image.

Everything else under `/etc/postfix` and `/etc/opendkim` is whatever the distro
installed. In particular `master.cf`, `postfix-files`, `postfix-script` and
`post-install` are **deliberately not** shipped from here — copying an older
Debian's copies over a freshly installed Postfix is how this breaks on the next
base-image bump.

## Secrets

Two files are required to build and are **gitignored** (`.gitignore`):

- `opendkim/keys/pente.org/mail.private` — DKIM private key
- `postfix/sasl_passwd` — SMTP relay credentials

They are baked into the image today. A build without them fails at the `COPY`
step, which is intentional: better a loud failure than a silently mis-signed
image. `sasl_passwd.db` is generated in the image with `postmap`, so it is never
stale relative to its source and is not tracked.

Moving these to runtime mounts / Docker secrets is the documented follow-up; it
would let the image itself be non-sensitive.

`opendkim/keys/pente.org/mail.txt` is the corresponding public DNS TXT record.
It is not needed by the image. The ignore rule deliberately covers only
`keys/**/*.private`, so `mail.txt` *can* be committed if you ever want the
published record under version control; it simply has not been.

## `MAIL_DOCKER_SUBNET`

The subnet the container should trust used to be stated twice and differently:
`mynetworks` in `main.cf` said `172.0.0.0/8`, `TrustedHosts` said
`172.18.0.0/16`. Since the `pente_org` network is a plain bridge with no pinned
subnet, Docker allocating outside `172.18/16` would leave mail relaying fine but
**silently unsigned**.

It is now stated once. `start_dsg_mail.sh` substitutes `@MAIL_DOCKER_SUBNET@`
into both files at container start and logs the effective value:

```
start_dsg_mail: trusted docker subnet = 172.0.0.0/8
```

Default is `172.0.0.0/8` — today's effective relay behaviour, and a widening of
the DKIM trust list that removes the silent-failure mode without loosening
`smtpd_relay_restrictions`.

The entrypoint fails the container rather than starting a broken mail system:
the value must look like an IPv4 CIDR, no `@MAIL_DOCKER_SUBNET@` may survive the
substitution, and `postfix check` must pass — all before any daemon starts.
Without those guards a bad render would leave `tail -f` holding the container
open while Postfix was dead.

### Recommended (not applied) compose change

Pin the network and state the subnet once, in `docker-compose.yml`:

```yaml
services:
  pente_mail:
    environment:
      - MAIL_DOCKER_SUBNET=172.20.0.0/16
networks:
  pente_org:
    driver: bridge
    name: pente_org
    ipam:
      config:
        - subnet: 172.20.0.0/16
```

With the subnet pinned, `MAIL_DOCKER_SUBNET` can be tightened from the `/8`
default to exactly that range.

## Healthcheck

`mail.Dockerfile` defines a `HEALTHCHECK` that checks the seam rather than the
process: it expects a `220 ... ESMTP` banner on `localhost:25` **and** a live
`opendkim`.

**Note:** `docker-compose.yml` currently defines its own `healthcheck:` for
`pente_mail` (`service postfix status`), and a compose-level healthcheck
overrides the image's. To pick up the banner check, delete that block from the
service definition. This was left unchanged here on purpose.
