#!/usr/bin/env python3
"""Publish a GitHub release with an APK asset. Uses the token embedded in
the origin remote URL (same credential flow as git push)."""
import json
import re
import subprocess
import sys
import urllib.request

REPO = "maca14313/callmony"


def token_from_remote() -> str:
    url = subprocess.check_output(
        ["git", "remote", "get-url", "origin"], text=True
    ).strip()
    m = re.search(r"://[^:]+:([^@]+)@", url)
    if not m:
        sys.exit("no token found in origin remote URL")
    return m.group(1)


def api(method: str, path: str, token: str, payload=None, raw=None, ctype="application/json"):
    req = urllib.request.Request(
        f"https://api.github.com{path}", method=method,
        headers={
            "Authorization": f"Bearer {token}",
            "Accept": "application/vnd.github+json",
            "User-Agent": "callmony-release-script",
            "Content-Type": ctype,
        },
        data=(json.dumps(payload).encode() if payload is not None else raw),
    )
    try:
        with urllib.request.urlopen(req) as r:
            return r.status, json.loads(r.read() or b"{}")
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read() or b"{}")


def main():
    tag, name, body, apk = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]
    tok = token_from_remote()

    # release (create or update)
    st, rel = api("POST", f"/repos/{REPO}/releases", tok, {
        "tag_name": tag, "name": name, "body": body,
        "target_commitish": "main", "draft": False, "prerelease": False,
    })
    if st not in (200, 201):
        # maybe exists — fetch it
        st2, rels = api("GET", f"/repos/{REPO}/releases/tags/{tag}", tok)
        rel = rels
    rid = rel.get("id")
    if not rid:
        sys.exit(f"release create failed: {st} {rel}")
    print(f"release id={rid}")

    # delete existing asset with the same name (re-run safety)
    st, assets = api("GET", f"/repos/{REPO}/releases/{rid}/assets", tok)
    for a in (assets or []):
        if a.get("name") == apk.split("/")[-1]:
            api("DELETE", f"/repos/{REPO}/releases/assets/{a['id']}", tok)
            print(f"deleted old asset {a['id']}")

    # upload
    data = open(apk, "rb").read()
    up = urllib.request.Request(
        f"https://uploads.github.com/repos/{REPO}/releases/{rid}/assets?name={apk.split('/')[-1]}",
        method="POST",
        headers={
            "Authorization": f"Bearer {tok}",
            "Accept": "application/vnd.github+json",
            "User-Agent": "callmony-release-script",
            "Content-Type": "application/vnd.android.package-archive",
        },
        data=data,
    )
    with urllib.request.urlopen(up) as r:
        print("asset:", r.status, json.loads(r.read()).get("browser_download_url"))


if __name__ == "__main__":
    main()
