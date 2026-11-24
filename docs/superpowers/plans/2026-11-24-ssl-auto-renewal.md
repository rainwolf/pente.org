# SSL Auto-Renewal with Live Reload Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Renew the pente.org cert daily on the prod host and load it live into Tomcat (443) and the game TLS sockets (15999-16010) with no container restart, with admin notifications on renewal and near expiry.

**Architecture:** Cron on the host runs getssl, which writes `~/dockerMain/ssl/keystore.p12` (PKCS12 built by openssl, atomic `mv`). The directory is bind-mounted read-only at `/etc/ssl/pente`. A daily Java `TimerTask` compares the disk cert with what 443 and a game port serve; when the disk one is newer it calls Tomcat's `reloadSslHostConfigs` over the platform MBean server and swaps the game sockets' reloadable key manager, then confirms and notifies.

**Tech Stack:** Java 21 (container JDK 21.0.13), Tomcat 11.0.29, JUnit 3 (`junit.textui.TestRunner` via `ant test`), getssl 2.52, openssl, fish/bash scripts, Docker Compose.

**Spec:** `docs/superpowers/specs/2026-11-24-ssl-auto-renewal-design.md`

## Global Constraints

- Keystore in container: `/etc/ssl/pente/keystore.p12`, type PKCS12, alias `tomcat`, password from env `SSL_PWD`.
- Host dir: `~/dockerMain/ssl/` (repo-relative `./dockerMain/ssl/`), mounted `./dockerMain/ssl:/etc/ssl/pente:ro`.
- Keystore writes go to a temp file in the same dir, then `mv` over `keystore.p12`.
- getssl server config: `ARI_ENABLE="false"`, `RENEW_ALLOW="60"`, `CHECK_REMOTE="false"`, CA `https://acme-v02.api.letsencrypt.org`, SANs `www.pente.org,submanifold.be,othmanw.submanifold.be,pentelive.submanifold.be`.
- Expiry alert: fewer than **45** days left on the served cert, one constant.
- No container restart in any renewal path. The one-time restart is at rollout (Task 8) only.
- No remote JMX port. Platform MBean server only.
- Never `npx`/fetch packages. `TMPDIR=/private/tmp/claude-501/ssl-renewal` for test runs. Nothing touches prod before Task 8, and Task 8 runs only with the user's explicit go-ahead per step.
- Commits: no co-author / generated-by lines.

## Review Focus

1. A game connection already open when the key manager swaps keeps working (no drop) → Task 1 test `testOpenConnectionSurvivesReload`.
2. Disk cert identical to served certs → no reload, no notification (no daily spam) → Task 3 test `testNoActionWhenCurrent`.
3. Keystore missing or unreadable at check time → ERROR log + one admin notification, listeners untouched → Task 3 test `testDiskReadFailureNotifies`.
4. Reload takes on 443 but not on the game port (or vice versa) → notification names the failing listener → Task 3 test `testPartialReloadNamesListener`.
5. Webapp context reload (`reloadable="true"`) → SSL check timer cancelled in `contextDestroyed`, no leaked thread → Task 5 step "verify timer cancel".

---

### Task 1: Reloadable key manager and served-cert reader

**Files:**
- Create: `dsg_src/java/org/pente/gameServer/server/ssl/SslKeystore.java`
- Create: `dsg_src/java/org/pente/gameServer/server/ssl/ReloadableKeyManager.java`
- Create: `dsg_src/java/org/pente/gameServer/server/ssl/GameSsl.java`
- Create: `dsg_src/java/org/pente/gameServer/server/ssl/ServedCertReader.java`
- Create: `dsg_src/java/org/pente/gameServer/server/ssl/test/TestKeystores.java`
- Create: `dsg_src/java/org/pente/gameServer/server/ssl/test/ReloadableKeyManagerTest.java`
- Modify: `build.xml` (add the test to the `test` target, same `<java classname="junit.textui.TestRunner">` pattern)

**Interfaces:**
- Produces:
  - `SslKeystore`: `static final Path PATH = Path.of("/etc/ssl/pente/keystore.p12")`, `static final String ALIAS = "tomcat"`, `static KeyStore load(Path file, char[] password) throws IOException, GeneralSecurityException`, `static X509Certificate leaf(KeyStore ks) throws KeyStoreException` (cert under `ALIAS`), `static char[] password()` (from `System.getenv("SSL_PWD")`, falling back to system property `SSL_PWD`).
  - `ReloadableKeyManager extends X509ExtendedKeyManager`: `ReloadableKeyManager(KeyStore ks, char[] pw) throws GeneralSecurityException`, `void reload(KeyStore ks, char[] pw) throws GeneralSecurityException`, `X509Certificate current()`. Delegates every `X509ExtendedKeyManager` method to an inner manager held in an `AtomicReference`, built with `KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())`.
  - `GameSsl`: `static synchronized SSLServerSocketFactory serverSocketFactory() throws IOException, GeneralSecurityException` (lazily loads `SslKeystore.PATH` once, one shared `ReloadableKeyManager` + `SSLContext("TLS")`), `static synchronized ReloadableKeyManager keyManager()`, `static SSLContext newContext(ReloadableKeyManager km) throws GeneralSecurityException`.
  - `ServedCertReader`: `static X509Certificate read(String host, int port, String sniHost, int timeoutMillis) throws IOException`. Trust-all handshake, reads the peer leaf cert, closes. Never sends application data.
  - Test helper `TestKeystores.create(Path dir, String name, int validDays, char[] pw) -> Path` shells out to `openssl req -x509 ... -days <validDays> -subj /CN=<name>` then `openssl pkcs12 -export -name tomcat`.

- [ ] **Step 1: Write failing tests** in `ReloadableKeyManagerTest extends TestCase`:
  - `testReloadSwapsCertificate`: km built from keystore A (1 day); `km.current().getNotAfter()` equals A's; after `km.reload(B)` (90 days) `km.current()` equals B's leaf and `km.getCertificateChain("tomcat")[0]` equals B's leaf.
  - `testServedCertFollowsReload`: `SSLServerSocket` on port 0 from `GameSsl.newContext(km).getServerSocketFactory()`, accept loop on a daemon thread doing a handshake per socket; `ServedCertReader.read("localhost", port, "pente.org", 5000)` returns A's leaf; after `km.reload(B)` it returns B's leaf.
  - `testOpenConnectionSurvivesReload`: open a client TLS connection (trust-all), echo one byte; `km.reload(B)`; echo another byte on the same connection succeeds.
- [ ] **Step 2: Run and see them fail**
  Run: `TMPDIR=/private/tmp/claude-501/ssl-renewal ant test` → Expected: compile FAIL (classes missing).
- [ ] **Step 3: Implement the four classes** per Interfaces.
- [ ] **Step 4: Run and see them pass**
  Run: `TMPDIR=/private/tmp/claude-501/ssl-renewal ant test` → Expected: `ReloadableKeyManagerTest` `OK (3 tests)`, all other suites still OK.
- [ ] **Step 5: Commit** `git add dsg_src/java/org/pente/gameServer/server/ssl build.xml && git commit -m "Add reloadable key manager for game TLS sockets"`

### Task 2: Game sockets use the shared reloadable context

**Files:**
- Modify: `dsg_src/java/org/pente/gameServer/server/Server.java:114-118`

**Interfaces:**
- Consumes: `GameSsl.serverSocketFactory()` (Task 1).

- [ ] **Step 1: Replace** the two `System.setProperty("javax.net.ssl.*")` lines and `SSLServerSocketFactory.getDefault()` with `GameSsl.serverSocketFactory().createServerSocket(port)`. Keep the existing `catch (Throwable t)` log + return. Drop the now-unused imports.
- [ ] **Step 2: Compile and run tests**
  Run: `TMPDIR=/private/tmp/claude-501/ssl-renewal ant test` → Expected: BUILD SUCCESSFUL, all suites OK.
- [ ] **Step 3: Grep for other default-SSL users**
  Run: `grep -rn "javax.net.ssl.keyStore\|SSLServerSocketFactory.getDefault" dsg_src/java` → Expected: no matches.
- [ ] **Step 4: Commit** `git commit -am "Open game TLS sockets from the reloadable SSL context"`

### Task 3: Certificate check logic

**Files:**
- Create: `dsg_src/java/org/pente/gameServer/server/ssl/SslListener.java`
- Create: `dsg_src/java/org/pente/gameServer/server/ssl/SslCertificateCheck.java`
- Create: `dsg_src/java/org/pente/gameServer/server/ssl/test/SslCertificateCheckTest.java`
- Modify: `build.xml` (register the test)

**Interfaces:**
- Produces:
  - `interface SslListener { String name(); X509Certificate served() throws Exception; void reload() throws Exception; }`
  - `final class SslCertificateCheck implements Runnable`: `static final int ALERT_DAYS = 45`; `SslCertificateCheck(Callable<X509Certificate> diskCert, List<SslListener> listeners, Consumer<String> notifier, Clock clock)`; `void run()` never throws (catches `Throwable`, logs ERROR via log4j `Category`, notifies).
- Behaviour of `run()`:
  1. Read disk cert. On failure → notify `"SSL certificate check failed: " + e` and return.
  2. Read each listener's served cert. A listener whose served `notAfter` is before disk `notAfter` gets `reload()`, then is re-read.
  3. If any listener was reloaded: all now equal disk → notify `"SSL certificate renewed, valid until " + <disk notAfter as yyyy-MM-dd, UTC>`; otherwise notify `"SSL certificate reload failed for: " + <comma-joined names still behind> + ", disk cert valid until " + <date>`.
  4. Days left = floor((min served notAfter − now) / 1 day). If `< ALERT_DAYS` → notify `"SSL certificate expires in " + days + " days (" + <date> + "), renewal is not working"`.
  5. A listener whose `served()` or `reload()` throws → notify `"SSL certificate check failed for " + name + ": " + e`, continue with the others.

- [ ] **Step 1: Write failing tests** (fake listeners holding a mutable `X509Certificate`, certs from `TestKeystores`, fixed `Clock`, notifier collecting into a `List<String>`):
  - `testNoActionWhenCurrent`: disk = served = 90-day cert, clock at issue → no reloads, zero notifications.
  - `testReloadsAndNotifiesRenewed`: served = 1-day cert, disk = 90-day; fake `reload()` sets served = disk → both listeners reloaded, exactly one message starting `"SSL certificate renewed, valid until "`.
  - `testPartialReloadNamesListener`: listener `"game"` ignores `reload()` → one message `"SSL certificate reload failed for: game, disk cert valid until ..."`.
  - `testExpiryAlertAt44DaysNotAt45`: served = disk; clock set to notAfter − 44 days → message `"SSL certificate expires in 44 days (...), renewal is not working"`; clock at notAfter − 45 days → no message.
  - `testDiskReadFailureNotifies`: `diskCert` throws `NoSuchFileException` → one message starting `"SSL certificate check failed: "`, no listener `reload()` called.
- [ ] **Step 2: Run, see fail** `TMPDIR=/private/tmp/claude-501/ssl-renewal ant test` → compile FAIL.
- [ ] **Step 3: Implement** per Interfaces and behaviour above.
- [ ] **Step 4: Run, see pass** → `SslCertificateCheckTest` `OK (5 tests)`.
- [ ] **Step 5: Commit** `git commit -m "Add daily SSL certificate check logic"` (with the new files and `build.xml`).

### Task 4: Mounted keystore directory (compose, server.xml, local dev cert)

**Files:**
- Modify: `docker-compose.yml:31` → `- ./dockerMain/ssl:/etc/ssl/pente:ro`
- Modify: `dockerMain/etctomcat9/server.xml:67-73` → `certificateKeystoreFile="/etc/ssl/pente/keystore.p12"`, `certificateKeystoreType="PKCS12"` (keep alias, password, type RSA)
- Modify: `create_local_ssl.sh` → `mkdir -p dockerMain/ssl`; `openssl pkcs12 -export ... -name tomcat` straight to `dockerMain/ssl/keystore.p12.tmp`, then `mv` to `dockerMain/ssl/keystore.p12`; delete the `keytool` line and the `MyDSKeyStore.jks` move; keep the error check (now on the `mv`) and the Android debug-cert copy unchanged. Keep `-legacy` only if the container JDK cannot load the file without it (Step 3 decides).
- Modify: `.gitignore` → add `dockerMain/ssl/`

- [ ] **Step 1: Generate the local cert** `./create_local_ssl.sh` → Expected: `dockerMain/ssl/keystore.p12` exists, `dev_localhost.crt` updated in the Android repo.
- [ ] **Step 2: Restart the local stack once** `docker compose up -d pente.org` (recreate for the new mount).
- [ ] **Step 3: Verify both listeners serve it**
  Run: `openssl s_client -connect localhost:443 -servername localhost </dev/null 2>/dev/null | openssl x509 -noout -subject -enddate` and the same for the local game port (first port from `docker compose port pente.org 15999` or the published mapping) → Expected: both show `CN=localhost` and the same `notAfter` as `openssl pkcs12 -in dockerMain/ssl/keystore.p12 -nokeys -passin env:SSL_PWD | openssl x509 -noout -enddate`. If the game port fails to load the keystore, retry Step 1 with `-legacy` and record which one works.
- [ ] **Step 4: `git status --porcelain`** → only the intended files changed; `dockerMain/ssl/` untracked-ignored.
- [ ] **Step 5: Commit** `git commit -m "Mount the SSL keystore as a directory, PKCS12"` (compose, server.xml, create_local_ssl.sh, .gitignore).

### Task 5: Tomcat JMX reload + daily timer wiring

**Files:**
- Create: `dsg_src/java/org/pente/gameServer/server/ssl/TomcatSslReloader.java`
- Modify: `dsg_src/java/org/pente/gameServer/server/DSGContextListener.java` (start in `contextInitialized` after `setupLiveGameServers`; cancel in `contextDestroyed` next to the other timers at ~line 513)

**Interfaces:**
- Consumes: `SslCertificateCheck`, `SslListener`, `SslKeystore`, `GameSsl`, `ServedCertReader` (Tasks 1, 3).
- Produces:
  - `TomcatSslReloader`: `static int reloadAll() throws JMException` → `ManagementFactory.getPlatformMBeanServer().queryNames(new ObjectName("Catalina:type=ThreadPool,name=*"), null)`, invoke `reloadSslHostConfigs` (no args) on every name whose `name` key contains `https`; return the count; throw `JMException` if zero. (Operation verified in Tomcat 11.0.29 `tomcat-coyote.jar` `org/apache/tomcat/util/net/mbeans-descriptors.xml`.)
  - In `DSGContextListener`: field `private Timer sslCheckTimer;`. Two listeners: `"https"` (served = `ServedCertReader.read("localhost", 443, "pente.org", 10000)`, reload = `TomcatSslReloader.reloadAll()`) and `"game"` (served = `ServedCertReader.read("localhost", <first started Server's port>, "pente.org", 10000)`, reload = `GameSsl.keyManager().reload(SslKeystore.load(SslKeystore.PATH, SslKeystore.password()), SslKeystore.password())`). Disk = `SslKeystore.leaf(SslKeystore.load(PATH, password()))`. Notifier = `resources.getNotificationServer()::sendAdminNotification`. Clock = `Clock.systemUTC()`.
  - Schedule: `new Timer("ssl-cert-check", true).scheduleAtFixedRate(check, 2 min, period)` where period = `Long.getLong("ssl.check.periodMinutes", 1440)` minutes.

- [ ] **Step 1: Implement** `TomcatSslReloader` and the wiring.
- [ ] **Step 2: Compile + unit tests** `TMPDIR=/private/tmp/claude-501/ssl-renewal ant test` → all OK.
- [ ] **Step 3: Deploy locally** per the local smoke-test flow (compile → classes bind-mounted → `docker compose restart pente.org`), with `-Dssl.check.periodMinutes=1` added to the local `JAVA_OPTS` override only (`docker-compose.override.yml`, not committed if it is machine-local).
- [ ] **Step 4: Live-reload end-to-end** rerun `./create_local_ssl.sh` (new self-signed cert, new `notAfter`) while the container keeps running; wait ≤ 2 minutes.
  Expected: `dockerMain/logs/tomcat/catalina.*.log` (or the app log) shows the check reloading both listeners; the Step 3 Task 4 `openssl s_client` commands now show the new `notAfter` on 443 and the game port; the container's `docker ps` uptime did not reset; an admin notification "SSL certificate renewed, valid until ..." was sent (check the notification log / admin device).
- [ ] **Step 5: Verify timer cancel** touch a class to trigger a context reload (or `docker compose restart` is not enough — use the webapp reload the repo already relies on) and confirm via `docker exec penteorg-pente.org-1 jcmd 1 Thread.print | grep -c ssl-cert-check` → Expected: `1`, not `2`. If `jcmd` is absent (JRE image), check the Tomcat log for the "web application ... started a thread named [ssl-cert-check]" leak warning instead → Expected: absent.
- [ ] **Step 6: Revert** the `periodMinutes=1` local override.
- [ ] **Step 7: Commit** `git commit -m "Daily SSL cert check with live reload of Tomcat and game sockets"`.

### Task 6: Server-side getssl config + keystore build script

**Files:**
- Create: `ssl-renewal/getssl.cfg` (global: `CA="https://acme-v02.api.letsencrypt.org"`, `ACCOUNT_KEY="/home/debian/letsencrypt/account.key"`, `PRIVATE_KEY_ALG="rsa"`, `ARI_ENABLE="false"`, `RENEW_ALLOW="60"`, `CHECK_REMOTE="false"`, no email)
- Create: `ssl-renewal/pente.org/getssl.cfg` (`SANS` per Global Constraints; `ACL` = five copies of `/home/debian/dockerMain/well-known/acme-challenge`; `DOMAIN_CERT_LOCATION="/home/debian/dockerMain/ssl/pente.org.crt"`, `DOMAIN_KEY_LOCATION="/home/debian/dockerMain/ssl/pente.org.key"`, `DOMAIN_CHAIN_LOCATION="/home/debian/dockerMain/ssl/fullchain.crt"`; `RELOAD_CMD="/home/debian/letsencrypt/build_keystore.sh"`; `DOMAIN_KEY_LENGTH=2048`)
- Create: `ssl-renewal/build_keystore.sh` (bash, `set -euo pipefail`; args default to `/home/debian/dockerMain/ssl`; reads `SSL_PWD` from `/home/debian/.env` if unset; `openssl pkcs12 -export -in fullchain.crt -inkey pente.org.key -name tomcat -passout env:SSL_PWD -out keystore.p12.tmp`; `chmod 600`; `mv keystore.p12.tmp keystore.p12`)

Note: files under `ssl-renewal/` carry no secrets. `account.key` is copied to the server by hand in Task 8, never committed. Whether the container UID can read a mode-600 file owned by `debian` is checked in Task 8; if not, use group-readable `640` with the container's group.

- [ ] **Step 1: Test the build script locally** against a self-signed pair in `/private/tmp/claude-501/ssl-renewal/ks` (`openssl req -x509 -newkey rsa:2048 -nodes -keyout pente.org.key -out fullchain.crt -days 2 -subj /CN=test`), run `SSL_PWD=test ssl-renewal/build_keystore.sh /private/tmp/claude-501/ssl-renewal/ks`.
  Expected: `keytool -list -keystore .../keystore.p12 -storetype PKCS12 -storepass test` lists alias `tomcat`, `PrivateKeyEntry`; no `keystore.p12.tmp` left; mode `-rw-------`.
- [ ] **Step 2: `bash -n ssl-renewal/build_keystore.sh`** → exit 0.
- [ ] **Step 3: Clean up** `rm -rf /private/tmp/claude-501/ssl-renewal/ks`.
- [ ] **Step 4: Commit** `git add ssl-renewal && git commit -m "Server-side getssl config and keystore build script"`.

### Task 7: Mac-side scripts (fallback push + DMG backup)

**Files:**
- Modify: `pushAndReload` → build `keystore.p12` with the same openssl command as `build_keystore.sh` (no `keytool`), `scp` it to `debian@pente.org:~/dockerMain/ssl/keystore.p12.tmp` then `ssh debian@pente.org mv ~/dockerMain/ssl/keystore.p12.tmp ~/dockerMain/ssl/keystore.p12`; copy into the DMG as `/Volumes/keysCertificatesStuff/keystore.p12`.
- Modify: `sync_gameServer.sh` (fish) → before the class sync: `open ~/mariposa/coding/keysCertificatesStuff.dmg`, `sleep 5`, `rsync -t --checksum debian@pente.org:~/dockerMain/ssl/keystore.p12 /Volumes/keysCertificatesStuff/keystore.p12`; on rsync failure `echo "WARNING: keystore backup to DMG failed"` and continue (no exit).
- `renewSSL` stays as is (fallback; its restart line is kept for emergency use, spec non-goal).

- [ ] **Step 1: Syntax checks** `bash -n pushAndReload` → 0; `fish -n sync_gameServer.sh` → 0.
- [ ] **Step 2: Read the diff** `git diff pushAndReload sync_gameServer.sh` → only the lines above changed. Do not run either script (both touch prod).
- [ ] **Step 3: Commit** `git commit -am "Point Mac SSL scripts at the mounted keystore dir"`.

### Task 8: Prod rollout (controller + user, every step confirmed by the user first)

No subagent. Each command touches prod; ask before each.

- [ ] **Step 1:** Build `keystore.p12` from the current cert (`letsencrypt/pente.org/fullchain.crt` + `pente.org.key`) with `build_keystore.sh` locally, `ssh debian@pente.org mkdir -p ~/dockerMain/ssl`, scp it there, mode 600.
- [ ] **Step 2:** Deploy `docker-compose.yml` + `server.xml` changes to `/home/debian` (respect the known compose drift: edit the deployed file's mount line, do not overwrite the deployed compose wholesale), deploy classes via `sync_gameServer.sh`, one `docker compose up -d pente.org`.
- [ ] **Step 3:** Verify from the Mac: `openssl s_client` on `pente.org:443` and `pente.org:15999` → both serve notAfter `Feb 22 02:41:11 2027 GMT`. Check the container can read the keystore (no Tomcat SSL errors in `catalina.*.log`).
- [ ] **Step 4:** Install getssl 2.52 + `ssl-renewal/*` into `/home/debian/letsencrypt/`, copy `letsencrypt/account.key` (mode 600), move `pente.org.key`/`fullchain.crt` into `~/dockerMain/ssl/` (mode 600). Run `~/letsencrypt/getssl -w ~/letsencrypt pente.org` once by hand → Expected: "certificate is valid for more than 60 days", exit 0, no CA order.
- [ ] **Step 5:** Install cron (`crontab -e` as `debian`): `17 4 * * * /home/debian/letsencrypt/getssl -w /home/debian/letsencrypt pente.org >> /home/debian/dockerMain/logs/getssl.log 2>&1`.
- [ ] **Step 6:** Within 24 h of Step 2, confirm the first scheduled Java check ran (log line, no notification since nothing changed).
