# SSL auto-renewal with live reload — design

Date: 2026-11-24. Status: revised after builder check, awaiting approval.

## Intent

Today the pente.org certificate is renewed by hand from the Mac (`renewSSL`): mount the encrypted
`keysCertificatesStuff.dmg`, run `getssl -f`, `pushAndReload` builds `MyDSKeyStore.jks` and scps it to
prod, then the `pente.org` container is restarted. The goal is to take the human out of the loop:
renewal happens on its own, every TLS listener picks up the new cert without a restart (a restart drops
every live game and WebSocket connection), and the admin hears about it on success and when renewal
has silently stopped working. Let's Encrypt no longer sends expiry emails, so without an alert a broken
renewal is only noticed when the site breaks.

## Goals

- G1: Daily, unattended renewal on the prod main host (`debian@51.79.69.199`, hostname `pente.org`).
- G2: All TLS listeners in the `pente.org` container load the renewed cert live, no container restart:
  - Tomcat HTTPS connector on 443.
  - The game server's raw TLS sockets (ports 15999-16010, used by the iOS and Android apps), opened in
    `dsg_src/java/org/pente/gameServer/server/Server.java:114-118`.
- G3: Admin notification (existing `NotificationServer.sendAdminNotification`) once a renewed cert is live
  on both.
- G4: Daily admin notification while the served cert is close to expiry (renewal is failing).

## Non-goals

- Changing the replica host.
- Replacing getssl with certbot/acme.sh.
- Removing the Mac-side `renewSSL`; it stays as a manual fallback.

## Anti-goals

- Any renewal path that restarts the `pente.org` container.
- An alert that can stay silent because the checker itself died: the Java check logs at ERROR and sends
  an admin notification when it cannot read a served cert or the keystore.
- 443 renewed while the game ports keep serving the old cert.

## Constraints (verified in repo 2026-11-24)

- One cert covers every name: SANs (subject alternative names, the extra hostnames on a cert)
  `pente.org, www.pente.org, submanifold.be, othmanw.submanifold.be, pentelive.submanifold.be`
  (`letsencrypt/pente.org/getssl.cfg:15`). All five resolve to 51.79.69.199 (dig, 2026-11-24), so the
  HTTP-01 challenge (Let's Encrypt fetching a token over plain HTTP to prove control of the name) can
  pass for all of them on the main host. Keep all SANs.
- Tomcat 11 (PR #71). One HTTPS connector `Http11NioProtocol` on 443 in
  `dockerMain/etctomcat9/server.xml` (bind-mounted as `/usr/local/tomcat/conf`), one `SSLHostConfig`,
  cert from `/var/lib/tomcat9/webapps/MyDSKeyStore.jks`, password `${SSL_PWD}`.
  `certificateKeystoreType="JKS"` is declared but `pushAndReload` actually builds a PKCS12 file
  (`-deststoretype pkcs12`).
- `Server.java:114-118` sets `javax.net.ssl.keyStore` to the same file and uses
  `SSLServerSocketFactory.getDefault()`. The JVM builds that default SSL context once and caches it, so
  Tomcat's reload does not reach the game ports.
- `docker-compose.yml:31` bind-mounts the keystore as a **single file**, which pins the inode: a file
  replaced by rename is invisible to the container. Fixed by D3.
- `well-known/` is bind-mounted into every ROOT webapp, so challenges can be written to
  `~/dockerMain/well-known/acme-challenge` on the host directly.
- getssl 2.52 has `ARI_ENABLE="true"` by default (`letsencrypt/getssl:320`). ARI (ACME Renewal
  Information) means the CA tells the client when to renew, and `RENEW_ALLOW` is ignored. getssl
  queries the CA's renewal-info endpoint on every run. For today's 90-day certs Let's Encrypt suggests
  renewing with about a third of the lifetime left (~day 60, from memory, unverified). ARI is turned
  off on the server (D4), so `RENEW_ALLOW="60"` governs.
- `CHECK_REMOTE="true"` in `letsencrypt/getssl.cfg:29`: after renewing, getssl checks the cert served on
  443 and fails if it differs. With the reload deferred to the Java check, that would fail every
  renewal. Set to false on the server (D5).
- Host RAM is tight (~3.7 GiB (gibibytes), Tomcat ~1.5 GiB). The host likely has no JDK, so no
  `keytool`. `openssl` is needed on the host (builder verifies it is installed).
- Existing timer pattern: `java.util.Timer` + `scheduleAtFixedRate` (e.g. `CacheDSGPlayerStorer`).

## Approaches considered

- O1 — **Live reload driven by a Java daily timer (chosen by user).** Cron only obtains the cert and
  writes the keystore. A daily in-JVM task compares the cert on disk with the ones being served and,
  when the disk one is newer, reloads Tomcat's connector over JMX and swaps the game-port key material,
  then confirms and notifies.
- O2 — Cron restarts the container after renewal (what `renewSSL` does now). Simple, but drops live
  games every renewal. Rejected by user.
- O3 — Keep renewing from the Mac via launchd. Needs the DMG password and an awake Mac. Rejected.

## Design

### Part 1 — renewal on the prod host (cron)

- Install the repo's pinned getssl (v2.52) and a server copy of the configs under
  `/home/debian/letsencrypt/`. Copy the current `account.key` once (not `account.key.compromised`),
  mode 600, owner `debian`.
- Server domain config:
  - `ACL` → local path `/home/debian/dockerMain/well-known/acme-challenge`.
  - Cert and key outputs → the new host directory `~/dockerMain/ssl/` (D3), mode 600 on the key.
  - `CHECK_REMOTE="false"` (D5). `ARI_ENABLE="false"` and `RENEW_ALLOW="60"` (D4).
  - `RELOAD_CMD` → a small script that builds the PKCS12 keystore with `openssl pkcs12 -export`
    (alias `tomcat`, password `SSL_PWD` from `~/.env`) into a temp file in `~/dockerMain/ssl/`, then
    `mv`s it over `keystore.p12`. Atomic rename is safe because the directory is mounted, not the file.
    No restart, no signal.
- Cron (user `debian`), daily at a fixed off-peak minute: `getssl pente.org` (no `-f`), output appended to
  a log under `~/dockerMain/logs/`.

### Part 2 — one-time switch to a mounted directory (needs one restart at rollout)

- Compose: replace the single-file mount with `./dockerMain/ssl:/etc/ssl/pente` (read-only).
- `server.xml`: `certificateKeystoreFile="/etc/ssl/pente/keystore.p12"`,
  `certificateKeystoreType="PKCS12"`.
- `Server.java`: stop relying on `SSLServerSocketFactory.getDefault()`. Build an `SSLContext` whose key
  manager is a reloadable wrapper (an `X509ExtendedKeyManager` delegating to a swappable inner manager
  loaded from `/etc/ssl/pente/keystore.p12`). All game `Server` instances share it. Swapping the inner
  manager makes every new handshake use the new cert. Connections already open keep running untouched,
  and no listener is rebound.
- Rollout order: deploy Part 2 with the current cert converted into `~/dockerMain/ssl/keystore.p12`,
  restart once, confirm 443 and 15999 both serve it, then enable the cron job.

### Part 3 — daily cert check + live reload (Java)

A new daily `TimerTask`, started at server boot next to the existing timers, also run once shortly
after boot:

1. Load `/etc/ssl/pente/keystore.p12` and take the cert's `notAfter`.
2. Read the cert served on 443 (TLS handshake to `localhost:443`, SNI `pente.org`) and on one game port
   (15999), take each `notAfter`.
3. If the disk cert is newer than either served cert:
   - 443: invoke Tomcat's SSL reload over the in-process platform MBean server.
   - Game ports: swap the reloadable key manager's inner manager.
   - Re-read both served certs. If both now match the disk cert → admin notification
     "SSL certificate renewed, valid until <date>". If not → admin notification naming which listener
     did not take it.
4. Expiry alert (D1): if the served cert's remaining validity is below the threshold, send a daily admin
   notification "SSL certificate expires in N days (<date>), renewal is not working".
5. Any exception in steps 1-3 → ERROR log + admin notification.

### Part 4 — local backup copy via `sync_gameServer.sh`

The Mac's encrypted `keysCertificatesStuff.dmg` used to receive each new keystore from `pushAndReload`
(`mv MyDSKeyStore.jks /Volumes/keysCertificatesStuff/`). With renewal on the server, that copy goes stale.

- `sync_gameServer.sh` (fish), on every run: unconditionally `open ~/mariposa/coding/keysCertificatesStuff.dmg`
  (same as `renewSSL`), sleep a few seconds, then pull `~/dockerMain/ssl/keystore.p12` from
  `debian@pente.org` into `/Volumes/keysCertificatesStuff/` (`rsync --checksum`, so it only copies on
  change). User's choice.
- If the pull fails (e.g. the volume is not mounted yet because the password prompt took longer than
  the sleep), print a warning and continue. A backup failure does not abort the deploy. My call.
- File name in the DMG: `keystore.p12` (D3 replaced the JKS). The old `MyDSKeyStore.jks` there is left
  alone.

### Part 5 — local dev cert (`create_local_ssl.sh`)

Local dev runs the same `docker-compose.yml` and `server.xml`, so the Part 2 mount change applies
locally too. `create_local_ssl.sh` currently writes `./dockerMain/MyDSKeyStore.jks`.

- Write the self-signed keystore to `./dockerMain/ssl/keystore.p12` instead (create the directory).
- Drop the `keytool` step: `openssl pkcs12 -export` (alias `tomcat`, password `SSL_PWD`) already
  produces the PKCS12 file Tomcat and `Server.java` read. Whether `-legacy` is still needed for the
  container's JDK is the builder's call (verify by loading it).
- Keep the Android debug-cert copy unchanged.
- This also gives the local test in "Left to the builder": rerun the script while the container runs, and
  the Java check should live-reload the new self-signed cert.

## Decisions

- D1: Expiry alert when fewer than **45 days** remain on the served cert. User's choice. With renewal
  starting at 60 days left (D4), that is 15 days of failed daily retries before the first alert, then
  45 days to fix it by hand before expiry. Keep 45 as one constant.
- D2: Live reload, not a restart. User's choice.
- D3: Single PKCS12 keystore in a bind-mounted directory, built by `openssl` alone. Both Tomcat and the
  game sockets read the same file, the directory mount removes the inode trap, and no host JDK is
  needed. (Revised from the first draft's PEM idea: the game sockets need a Java keystore anyway.)
  My call.
- D4: ARI off (`ARI_ENABLE="false"`), fixed `RENEW_ALLOW="60"`: renewal starts at 60 days left (~day 30
  of a 90-day cert). User's choice, over ARI (which renews ~30 days left and could pull renewal earlier
  during a Let's Encrypt mass revocation; the daily expiry alert covers that case).
  **Both numbers must drop when Let's Encrypt shortens cert lifetimes:** with a 64-day cert,
  `RENEW_ALLOW=60` would try to renew every day and hit the 5-identical-certs-per-week rate limit, and a
  45-day alert would fire permanently on a 45-day cert.
- D5: `CHECK_REMOTE="false"` on the server. Verification moves to the Java check, which runs after the
  reload. My call.
- D6: Game ports in scope, live-swapped via a reloadable key manager (G2). My call.

## Security

- New on the server: the ACME account key. Its marginal power is revoking or reissuing certs. Anyone
  with control of the host can already pass HTTP-01 for these names, so the added risk is small.
- Key and keystore files mode 600, owner `debian`, outside every web root, never in the repo
  (`letsencrypt/` is in `.gitignore:7`). The container reads the keystore read-only.
- The JMX reload runs in-process. No remote JMX port is opened.

## Left to the builder

- Exact Tomcat 11 MBean object name and operation for the SSL reload (verify against the Tomcat 11 jar).
- Which class owns the timer, and how it gets a `NotificationServer` reference.
- Whether the container's UID can read a mode-600 `debian`-owned file. If not, pick group/mode so it can.
- Cron minute, log rotation, getssl install path.
- Test: on a local container, swap a new self-signed keystore into the mounted dir and watch both 443
  and 15999 pick it up with no restart and the notification fire. Then one forced renewal on prod.
