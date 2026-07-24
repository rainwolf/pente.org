# pentedb / `/api/db` — Owner Deploy Checklist

This is an **owner-run** checklist. Nothing in this document should be executed by an agent —
it is documentation only. It covers deploying the `react_pentedb` app (served under
`~/dockerMain/gameServer/db/`) together with its backend, `WebDbApiServlet`
(`/api/db/*`, already wired into `dsg_src/httpdocs/WEB-INF/web.xml` as of Phase 1).

## Prerequisites

- `WebDbApiServlet` mapping already present in `dsg_src/httpdocs/WEB-INF/web.xml`
  (`<servlet-name>WebDbApiServlet</servlet-name>` / `<url-pattern>/api/db/*</url-pattern>`) —
  verify it's still there before proceeding; no further web.xml edits should be needed for this
  deploy.
- `sync_gameServer.sh` has been extended to build `react_pentedb` (alongside `react_mmai` /
  `react_live_game_room`) and rsync its `build/` output to
  `debian@pente.org:~/dockerMain/gameServer/db/`.

## Steps (in order)

1. **Apply the SQL migration to the production DB.**
   Run `dsg_src/sql/2026-07-18-webdb-tables.sql` against the production database before anything
   else — the servlet will error on requests that touch tables it doesn't find. The tables this
   migration adds are purely additive (no destructive changes to existing schema), so this step
   is safe to run ahead of the code deploy.

2. **Rebuild and deploy the app image:** `build_and_deploy.sh pente.org`.
   A full image rebuild is required here, not just a hot class-file sync — `web.xml` is `COPY`'d
   into the Docker image at build time (`Dockerfile`) and is **not** one of the volumes/paths
   `docker-compose.yml` hot-mounts. A running container keeps using whatever `web.xml` was baked
   into it at its last build, so the `/api/db/*` servlet mapping only takes effect once this step
   completes (see `build-deploy-serving.md` §2 for the full evidence trail).

3. **Sync the React build:** `sync_gameServer.sh`.
   This builds and rsyncs `react_pentedb/build/` to
   `debian@pente.org:~/dockerMain/gameServer/db/`, alongside the existing `live/` and `mmai/`
   syncs.

4. **Verify:**
   - `https://pente.org/api/db/ping` responds (confirms the servlet is live post-rebuild).
   - The app itself loads at `https://pente.org/gameServer/db/`.

5. **Rollback, if needed:** redeploy the previous image (pre-dating this change) and/or remove
   the `WebDbApiServlet` mapping from `web.xml` before the next rebuild. The SQL migration's
   tables are additive and safe to leave in place — no down-migration is required as part of a
   rollback.

## Caveat carried over from `build-deploy-serving.md` §1

The mapping between `sync_gameServer.sh`'s rsync targets (`~/dockerMain/gameServer/...`) and what
`docker-compose.yml` / the `Dockerfile` actually mount or `COPY` into the running container could
**not** be fully confirmed from the files in this repository alone — the same discrepancy already
flagged for the existing `live/` and `mmai/` targets applies to the new `db/` target. Before
relying on step 3 above, the owner should verify **on the production host** that
`~/dockerMain/gameServer/db/` actually lands where Tomcat serves `gameServer/db/` from (i.e. run
the same on-host check that would be needed to explain how `live/` and `mmai/` currently work) —
don't assume it "just works" by analogy alone.
