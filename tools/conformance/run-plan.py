#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
# SPDX-License-Identifier: Apache-2.0

"""Run an OpenID Foundation test plan through its HTTP API.

The suite itself runs in containers; this small standard-library-only client
keeps the repository independent of the suite's Python dependencies and makes
the same runner usable from a local just command and GitHub Actions.
"""

from __future__ import annotations

import argparse
import json
import os
import ssl
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import zipfile
from pathlib import Path
from string import Template


TERMINAL_STATUSES = {"FINISHED", "INTERRUPTED"}
TERMINAL_RESULTS = {
    "PASSED",
    "FAILED",
    "WARNING",
    "SKIPPED",
    "REVIEW",
    "REVIEWED",
    "CONDITIONAL",
}


class SuiteClient:
    def __init__(self, base_url: str):
        self.base_url = base_url.rstrip("/")
        self.context = ssl._create_unverified_context()

    def request(
        self,
        method: str,
        path: str,
        *,
        query: dict[str, str] | None = None,
        body: bytes | None = None,
        accept: str = "application/json",
    ) -> tuple[int, bytes]:
        url = f"{self.base_url}{path}"
        if query:
            url += "?" + urllib.parse.urlencode(query)
        request = urllib.request.Request(
            url,
            data=body,
            method=method,
            headers={"Accept": accept, "Content-Type": "application/json"},
        )
        try:
            with urllib.request.urlopen(request, context=self.context, timeout=30) as response:
                return response.status, response.read()
        except urllib.error.HTTPError as error:
            detail = error.read().decode("utf-8", errors="replace")
            raise RuntimeError(f"{method} {url} returned HTTP {error.code}: {detail}") from error

    def json(self, method: str, path: str, **kwargs: object) -> object:
        _, body = self.request(method, path, **kwargs)
        return json.loads(body)

    def download(self, path: str, destination: Path) -> None:
        _, body = self.request("GET", path, accept="application/zip")
        destination.write_bytes(body)


def wait_for_suite(client: SuiteClient, timeout: int) -> None:
    deadline = time.monotonic() + timeout
    last_error: Exception | None = None
    while time.monotonic() < deadline:
        try:
            client.json("GET", "/api/runner/available")
            return
        except Exception as error:  # noqa: BLE001 - retry until the suite is ready
            last_error = error
            time.sleep(2)
    raise RuntimeError(f"Conformance suite did not become ready: {last_error}")


def resolve_config(template_path: Path, destination: Path) -> dict[str, object]:
    config_text = Template(template_path.read_text()).substitute(os.environ)
    config = json.loads(config_text)
    destination.write_text(json.dumps(config, indent=2) + "\n")
    return config


def create_plan(client: SuiteClient, config: dict[str, object]) -> str:
    query = {
        "planName": "openid-federation-deployed-entity-test-plan",
        "variant": json.dumps(
            {"server_metadata": "discovery", "client_registration": "automatic"},
            separators=(",", ":"),
        ),
    }
    status, body = client.request(
        "POST",
        "/api/plan",
        query=query,
        body=json.dumps(config).encode(),
    )
    if status != 201:
        raise RuntimeError(f"Conformance suite created no plan: HTTP {status}")
    return str(json.loads(body)["id"])


def run_module(
    client: SuiteClient,
    plan_id: str,
    module: dict[str, object],
    timeout: int,
) -> tuple[str, str, str]:
    module_name = str(module["testModule"])
    variant = module.get("variant") or {}
    runner = client.json(
        "POST",
        "/api/runner",
        query={
            "test": module_name,
            "plan": plan_id,
            "variant": json.dumps(variant, separators=(",", ":")),
        },
    )
    runner_id = str(runner["id"])
    deadline = time.monotonic() + timeout
    last_info: dict[str, object] = {}
    while time.monotonic() < deadline:
        last_info = client.json("GET", f"/api/info/{runner_id}")
        status = str(last_info.get("status", ""))
        result = str(last_info.get("result", ""))
        if status in TERMINAL_STATUSES or result in TERMINAL_RESULTS:
            print(f"{module_name}: {result or status} ({runner_id})", flush=True)
            return module_name, result or status, runner_id
        time.sleep(2)
    raise RuntimeError(f"{module_name} did not finish: {json.dumps(last_info)}")


def export_results(client: SuiteClient, plan_id: str, output_dir: Path) -> None:
    archive = output_dir / f"plan-{plan_id}.zip"
    try:
        client.download(f"/api/plan/exporthtml/{plan_id}", archive)
        with zipfile.ZipFile(archive) as zip_file:
            zip_file.extractall(output_dir / f"plan-{plan_id}")
    except Exception as error:  # noqa: BLE001 - logs should not hide the test result
        print(f"Could not export HTML results: {error}", file=sys.stderr)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--suite-url", required=True)
    parser.add_argument("--config-template", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--suite-timeout", type=int, default=180)
    parser.add_argument("--module-timeout", type=int, default=900)
    args = parser.parse_args()

    args.output_dir.mkdir(parents=True, exist_ok=True)
    resolved_config_path = args.output_dir / "plan-config.json"
    config = resolve_config(args.config_template, resolved_config_path)
    client = SuiteClient(args.suite_url)
    wait_for_suite(client, args.suite_timeout)
    plan_id = create_plan(client, config)
    plan = client.json("GET", f"/api/plan/{plan_id}")
    modules = plan.get("modules", [])
    if not modules:
        raise RuntimeError(f"Plan {plan_id} contained no test modules")

    print(f"Created Federation conformance plan {plan_id} with {len(modules)} modules", flush=True)
    results: list[dict[str, str]] = []
    for module in modules:
        name, result, runner_id = run_module(client, plan_id, module, args.module_timeout)
        results.append({"module": name, "result": result, "runner_id": runner_id})
        try:
            _, log = client.request("GET", f"/log-detail.html?log={runner_id}")
            (args.output_dir / f"log-{runner_id}.html").write_bytes(log)
        except Exception as error:  # noqa: BLE001 - preserve the primary result
            print(f"Could not save {name} log: {error}", file=sys.stderr)

    export_results(client, plan_id, args.output_dir)
    (args.output_dir / "summary.json").write_text(
        json.dumps({"plan_id": plan_id, "results": results}, indent=2) + "\n"
    )
    # The deployed-entity plan includes the optional resolve endpoint check. A
    # provider that does not advertise federation_resolve_endpoint is correctly
    # reported as SKIPPED by the official suite; failed and review-needed tests
    # must still fail this command.
    failed = [entry for entry in results if entry["result"] not in {"PASSED", "SKIPPED"}]
    if failed:
        print(f"Federation conformance failed: {json.dumps(failed)}", file=sys.stderr)
        return 1
    skipped = [entry for entry in results if entry["result"] == "SKIPPED"]
    if skipped:
        print(
            f"Federation conformance passed with skipped optional modules: {json.dumps(skipped)}",
            flush=True,
        )
    else:
        print("Federation conformance passed", flush=True)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as error:  # noqa: BLE001 - concise CI failure with logs preserved
        print(f"Federation conformance runner failed: {error}", file=sys.stderr)
        raise SystemExit(1)
