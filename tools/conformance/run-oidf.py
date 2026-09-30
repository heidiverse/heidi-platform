#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
# SPDX-License-Identifier: Apache-2.0

"""Drive the official OpenID Foundation OID4VCI and OID4VP plans.

The suite acts as the wallet. Heidi is exercised through its public HTTPS
endpoints, while process setup uses the server-to-server integration API.
Keeping this client stdlib-only makes it usable from a clean CI runner.
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
import uuid
import zipfile
from pathlib import Path
from typing import Any


TERMINAL_STATUSES = {"FINISHED", "INTERRUPTED"}
PASS_RESULT = "PASSED"
SKIP_RESULT = "SKIPPED"
REVIEW_RESULT = "REVIEW"

ISSUER_PLAN = "oid4vci-1_0-issuer-test-plan"
VERIFIER_PLAN = "oid4vp-1final-verifier-test-plan"
ISSUER_VARIANT = {
    "client_auth_type": "private_key_jwt",
    "sender_constrain": "dpop",
    "credential_format": "sd_jwt_vc",
    "vci_authorization_code_flow_variant": "issuer_initiated",
    "authorization_request_type": "simple",
    "openid": "plain_oauth",
    "fapi_request_method": "unsigned",
    "vci_grant_type": "pre_authorization_code",
    "vci_credential_encryption": "plain",
    "fapi_profile": "vci",
    "fapi_response_mode": "plain_response",
}
VERIFIER_VARIANT = {
    "credential_format": "sd_jwt_vc",
    "client_id_prefix": "x509_hash",
    "request_method": "request_uri_signed",
    "vp_profile": "plain_vp",
    "response_mode": "direct_post.jwt",
}


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):  # type: ignore[no-untyped-def]
        return None


class HttpClient:
    def __init__(self, base_url: str, headers: dict[str, str] | None = None):
        self.base_url = base_url.rstrip("/")
        self.headers = headers or {}
        self.context = ssl._create_unverified_context()

    def request(
        self,
        method: str,
        path: str,
        *,
        query: dict[str, str] | None = None,
        body: object | None = None,
        accept: str = "application/json",
        headers: dict[str, str] | None = None,
        follow_redirects: bool = True,
    ) -> tuple[int, bytes]:
        url = path if path.startswith("http") else f"{self.base_url}{path}"
        if query:
            separator = "&" if "?" in url else "?"
            url += separator + urllib.parse.urlencode(query)
        payload = None if body is None else json.dumps(body).encode("utf-8")
        request_headers = {"Accept": accept, **self.headers, **(headers or {})}
        if body is not None:
            request_headers["Content-Type"] = "application/json"
        request = urllib.request.Request(url, data=payload, method=method, headers=request_headers)
        try:
            if follow_redirects:
                response = urllib.request.urlopen(request, context=self.context, timeout=60)
            else:
                opener = urllib.request.build_opener(
                    urllib.request.HTTPSHandler(context=self.context), NoRedirect()
                )
                response = opener.open(request, timeout=60)
            with response:
                return response.status, response.read()
        except urllib.error.HTTPError as error:
            if not follow_redirects and 300 <= error.code < 400:
                return error.code, error.read()
            detail = error.read().decode("utf-8", errors="replace")
            raise RuntimeError(f"{method} {url} returned HTTP {error.code}: {detail}") from error

    def json(self, method: str, path: str, **kwargs: object) -> Any:
        _, payload = self.request(method, path, **kwargs)
        if not payload:
            return None
        return json.loads(payload)

    def download(self, path: str, destination: Path) -> None:
        _, payload = self.request("GET", path, accept="application/zip")
        destination.write_bytes(payload)


def substitute(value: Any) -> Any:
    """Resolve ${VAR} strings after parsing JSON, preserving multiline PEMs."""
    if isinstance(value, list):
        return [substitute(item) for item in value]
    if isinstance(value, dict):
        return {key: substitute(item) for key, item in value.items()}
    if not isinstance(value, str):
        return value

    if value.startswith("${") and value.endswith("}") and value.count("${") == 1:
        return os.environ.get(value[2:-1], "")
    result = value
    for key, env_value in os.environ.items():
        result = result.replace("${" + key + "}", env_value)
    return result


def read_config(path: Path) -> dict[str, Any]:
    return substitute(json.loads(path.read_text(encoding="utf-8")))


def wait_for_suite(client: HttpClient, timeout: int) -> None:
    deadline = time.monotonic() + timeout
    last_error: Exception | None = None
    while time.monotonic() < deadline:
        try:
            client.json("GET", "/api/runner/available")
            return
        except Exception as error:  # noqa: BLE001 - retry until ready
            last_error = error
            time.sleep(2)
    raise RuntimeError(f"Conformance suite did not become ready: {last_error}")


def create_plan(
    suite: HttpClient,
    plan_name: str,
    variant: dict[str, str],
    body: dict[str, Any],
) -> str:
    body = dict(body)
    alias = str(body.get("alias", "heidi-oidf"))
    if os.getenv("CONFORMANCE_FIXED_ALIASES", "false").lower() != "true":
        alias = f"{alias}-{uuid.uuid4().hex[:8]}"
    body["alias"] = alias
    response = suite.json(
        "POST",
        "/api/plan",
        query={"planName": plan_name, "variant": json.dumps(variant, separators=(",", ":"))},
        body=body,
    )
    if not isinstance(response, dict) or not response.get("id"):
        raise RuntimeError(f"Conformance suite created no {plan_name} plan: {response}")
    plan_id = str(response["id"])
    print(f"Created {plan_name} plan {plan_id}", flush=True)
    return plan_id


def start_runner(suite: HttpClient, plan_id: str, module: dict[str, Any]) -> dict[str, Any]:
    variant = module.get("variant") or {}
    return suite.json(
        "POST",
        "/api/runner",
        query={
            "test": str(module["testModule"]),
            "plan": plan_id,
            "variant": json.dumps(variant, separators=(",", ":")),
        },
    )


def wait_runner(
    suite: HttpClient,
    runner_id: str,
    timeout: int,
    *,
    waiting: bool = False,
) -> dict[str, Any]:
    deadline = time.monotonic() + timeout
    last: dict[str, Any] = {}
    while time.monotonic() < deadline:
        last = suite.json("GET", f"/api/info/{runner_id}") or {}
        if waiting and str(last.get("status")) == "WAITING":
            return last
        if not waiting and (
            str(last.get("status")) in TERMINAL_STATUSES
            or str(last.get("result")) in {PASS_RESULT, SKIP_RESULT, "FAILED", "WARNING", "REVIEW"}
        ):
            return last
        time.sleep(2)
    raise RuntimeError(f"Runner {runner_id} did not finish: {json.dumps(last)}")


def exposed_endpoint(suite: HttpClient, runner_id: str, timeout: int) -> str:
    deadline = time.monotonic() + timeout
    last: Any = None
    while time.monotonic() < deadline:
        last = suite.json("GET", f"/api/runner/{runner_id}")
        endpoint = (last or {}).get("exposed", {}).get("credential_offer_endpoint")
        if endpoint:
            if not str(endpoint).startswith("https://"):
                raise RuntimeError(f"Expected HTTPS credential_offer_endpoint, got {endpoint}")
            return str(endpoint)
        info = suite.json("GET", f"/api/info/{runner_id}") or {}
        if str(info.get("status")) in TERMINAL_STATUSES:
            raise RuntimeError(
                f"Runner {runner_id} stopped before exposing a credential offer: "
                f"{info.get('result') or info.get('status')}"
            )
        time.sleep(1)
    raise RuntimeError(f"Runner {runner_id} exposed no credential_offer_endpoint: {last}")


def append_query(endpoint: str, query_path: str) -> str:
    if not query_path:
        raise RuntimeError("Heidi returned an empty wallet hand-off path")
    if not query_path.startswith("?"):
        query_path = "?" + query_path
    separator = "&" if "?" in endpoint else "?"
    return endpoint + separator + query_path[1:]


def interaction_path(interaction: dict[str, Any], *, same_device: bool) -> str:
    key = "sameDevice" if same_device else "crossDevice"
    process_data = interaction.get(key) or {}
    path = process_data.get("qrCodeDataPath")
    if not path:
        other = "crossDevice" if same_device else "sameDevice"
        path = (interaction.get(other) or {}).get("qrCodeDataPath")
    if not path:
        raise RuntimeError(f"Heidi interaction response has no {key} wallet hand-off: {interaction}")
    return str(path)


def runner_path(runner_url: str) -> str:
    """Use the suite path locally; its advertised host is for containers."""
    parsed = urllib.parse.urlsplit(runner_url)
    path = parsed.path if parsed.scheme else runner_url
    if not path.startswith("/"):
        path = "/" + path
    return path.rstrip("/")


def drive_issuer(
    suite: HttpClient,
    platform: HttpClient,
    endpoint: str,
    *,
    credential_identifier: str,
    credential_version: str,
    issuer_slug: str,
    integration_auth: str,
) -> None:
    initialization = platform.json(
        "POST",
        "/integration/v1/processes",
        body={
            "action": "pre_auth_issuance",
            "preAuthIssuanceData": {
                "schemaIdentifier": {
                    "credentialIdentifier": credential_identifier,
                    "version": credential_version,
                },
                "values": {"given_name": "Ada", "family_name": "Lovelace"},
                "issuerSlug": issuer_slug,
                "includeTxCode": False,
                "credentialOfferType": "VALUE",
            },
        },
    )
    process_id = str(initialization["processId"])
    started = platform.json(
        "POST",
        f"/integration/v1/processes/{process_id}/start",
        body={"processToken": initialization["processToken"]},
    )
    interaction = platform.json(
        "GET",
        "/interaction/v1/processes/current",
        headers={"Authorization": f"Bearer {started['clientInteractionToken']}"},
    )
    offer_path = interaction_path(interaction, same_device=False)
    suite.request("GET", append_query(endpoint, offer_path), accept="text/html")


def drive_verifier(
    suite: HttpClient,
    platform: HttpClient,
    runner_url: str,
    *,
    proof_scheme_id: str,
    integration_auth: str,
) -> None:
    initialization = platform.json(
        "POST",
        "/integration/v1/processes",
        body={
            "action": "presentation",
            "presentationData": {
                "proofSchemeId": proof_scheme_id,
                "useDcApi": False,
                "oid4vpVersion": "DRAFT_28",
            },
            "includeVpToken": False,
        },
    )
    process_id = str(initialization["processId"])
    started = platform.json(
        "POST",
        f"/integration/v1/processes/{process_id}/start",
        body={"processToken": initialization["processToken"]},
    )
    interaction = platform.json(
        "GET",
        "/interaction/v1/processes/current",
        headers={"Authorization": f"Bearer {started['clientInteractionToken']}"},
    )
    request_path = interaction_path(interaction, same_device=True)
    suite.request(
        "GET",
        f"{runner_path(runner_url)}/authorize{request_path}",
        accept="text/html",
        follow_redirects=False,
    )


def run_module(
    suite: HttpClient,
    platform: HttpClient,
    plan_id: str,
    module: dict[str, Any],
    role: str,
    args: argparse.Namespace,
) -> dict[str, Any]:
    name = str(module["testModule"])
    started_at = time.monotonic()
    runner = start_runner(suite, plan_id, module)
    runner_id = str(runner["id"])
    try:
        if role == "issuer" and "-metadata-test" in name:
            info = wait_runner(suite, runner_id, args.module_timeout)
        elif role == "issuer":
            endpoint = exposed_endpoint(suite, runner_id, args.module_timeout)
            drive_issuer(
                suite,
                platform,
                endpoint,
                credential_identifier=args.credential_identifier,
                credential_version=args.credential_version,
                issuer_slug=args.issuer_slug,
                integration_auth=args.integration_auth,
            )
            info = wait_runner(suite, runner_id, args.module_timeout)
        else:
            waiting = wait_runner(suite, runner_id, args.module_timeout, waiting=True)
            if str(waiting.get("status")) != "WAITING":
                raise RuntimeError(
                    f"Runner {runner_id} stopped before wallet interaction: "
                    f"{waiting.get('result') or waiting.get('status')}"
                )
            runner_url = str(runner.get("url") or "")
            if not runner_url:
                runner_details = suite.json("GET", f"/api/runner/{runner_id}") or {}
                runner_url = str(runner_details.get("url") or "")
            if not runner_url:
                raise RuntimeError(f"Runner {runner_id} exposed no authorization URL")
            drive_verifier(
                suite,
                platform,
                runner_url,
                proof_scheme_id=args.proof_scheme_id,
                integration_auth=args.integration_auth,
            )
            info = wait_runner(suite, runner_id, args.module_timeout)
        result = str(info.get("result") or info.get("status") or "UNKNOWN")
        print(f"{role}/{name}: {result} ({runner_id})", flush=True)
        return {
            "role": role,
            "module": name,
            "result": result,
            "status": str(info.get("status", "")),
            "runner_id": runner_id,
            "duration_seconds": round(time.monotonic() - started_at, 2),
            "optional": bool(module.get("optional") or module.get("mandatory") is False),
        }
    except Exception as error:  # noqa: BLE001 - preserve runner id in summary
        return {
            "role": role,
            "module": name,
            "result": "ERROR",
            "status": "ERROR",
            "runner_id": runner_id,
            "error": str(error),
            "duration_seconds": round(time.monotonic() - started_at, 2),
            "optional": False,
        }


def export_plan(suite: HttpClient, plan_id: str, output_dir: Path) -> None:
    archive = output_dir / f"plan-{plan_id}.zip"
    try:
        suite.download(f"/api/plan/exporthtml/{plan_id}", archive)
        with zipfile.ZipFile(archive) as zip_file:
            zip_file.extractall(output_dir / f"plan-{plan_id}")
    except Exception as error:  # noqa: BLE001 - keep test outcome primary
        print(f"Could not export plan {plan_id}: {error}", file=sys.stderr)


def export_log(suite: HttpClient, runner_id: str, output_dir: Path) -> None:
    try:
        _, log = suite.request("GET", f"/log-detail.html?log={runner_id}", accept="text/html")
        (output_dir / f"log-{runner_id}.html").write_bytes(log)
    except Exception as error:  # noqa: BLE001 - keep test outcome primary
        print(f"Could not export runner {runner_id} log: {error}", file=sys.stderr)


def run_plan(
    suite: HttpClient,
    platform: HttpClient,
    *,
    role: str,
    plan_name: str,
    variant: dict[str, str],
    config_path: Path,
    output_dir: Path,
    args: argparse.Namespace,
) -> list[dict[str, Any]]:
    output_dir.mkdir(parents=True, exist_ok=True)
    config = read_config(config_path)
    (output_dir / "plan-config.json").write_text(
        json.dumps(config, indent=2) + "\n", encoding="utf-8"
    )
    plan_id = create_plan(suite, plan_name, variant, config)
    plan = suite.json("GET", f"/api/plan/{plan_id}")
    modules = plan.get("modules", []) if isinstance(plan, dict) else []
    if not modules:
        raise RuntimeError(f"Plan {plan_id} contained no test modules")
    module_filter = os.getenv("CONFORMANCE_OIDF_MODULES", "") or os.getenv(
        f"CONFORMANCE_OIDF_{role.upper()}_MODULES",
        os.getenv("OIDF_MODULES", os.getenv("VITE_OIDF_MODULES", "")),
    )
    filters = {
        name.strip()
        for name in module_filter.split(",")
        if name.strip()
    }
    pattern = os.getenv(
        "CONFORMANCE_OIDF_MODULE_PATTERN",
        os.getenv("OIDF_MODULE_PATTERN", os.getenv("VITE_OIDF_MODULE_PATTERN", "")),
    )
    if filters:
        modules = [module for module in modules if module.get("testModule") in filters]
    if pattern:
        import re

        modules = [module for module in modules if re.search(pattern, str(module.get("testModule", "")))]
    if not modules:
        raise RuntimeError(f"No modules matched the configured filter for plan {plan_id}")
    results: list[dict[str, Any]] = []
    for module in modules:
        result = run_module(suite, platform, plan_id, module, role, args)
        result["plan_id"] = plan_id
        results.append(result)
        export_log(suite, result["runner_id"], output_dir)
    export_plan(suite, plan_id, output_dir)
    return results


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--suite-url", default=os.getenv("CONFORMANCE_SUITE_URL", "https://127.0.0.1:8443"))
    parser.add_argument("--platform-url", default=os.getenv("HEIDI_PLATFORM_URL", "https://127.0.0.1:8080"))
    parser.add_argument("--issuer-plan", type=Path, default=Path("tools/conformance/oid4vci-plan.json"))
    parser.add_argument("--verifier-plan", type=Path, default=Path("tools/conformance/oid4vp-plan.json"))
    parser.add_argument("--output-dir", type=Path, default=Path("tmp/conformance/oidf"))
    parser.add_argument("--suite-timeout", type=int, default=180)
    parser.add_argument("--module-timeout", type=int, default=900)
    parser.add_argument("--issuer-slug", default=os.getenv("HEIDI_CONFORMANCE_IDENTITY", "acme"))
    parser.add_argument("--credential-identifier", default=os.getenv("HEIDI_CONFORMANCE_CREDENTIAL_IDENTIFIER", "pid"))
    parser.add_argument("--credential-version", default=os.getenv("HEIDI_CONFORMANCE_CREDENTIAL_VERSION", "1.0"))
    parser.add_argument("--proof-scheme-id", default=os.getenv("HEIDI_CONFORMANCE_PROOF_SCHEME_ID", ""))
    parser.add_argument("--integration-auth", default=os.getenv("HEIDI_CONFORMANCE_INTEGRATION_AUTH", "Bearer no-security"))
    parser.add_argument("--skip-issuer", action="store_true")
    parser.add_argument("--skip-verifier", action="store_true")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    args.output_dir.mkdir(parents=True, exist_ok=True)
    os.environ.setdefault("HEIDI_CONFORMANCE_CREDENTIAL_IDENTIFIER", args.credential_identifier)
    os.environ.setdefault(
        "HEIDI_CONFORMANCE_CREDENTIAL_CONFIGURATION_ID",
        f"{args.credential_identifier}-{args.credential_version}-sd-jwt",
    )
    suite = HttpClient(args.suite_url)
    platform = HttpClient(args.platform_url, {"Authorization": args.integration_auth})
    wait_for_suite(suite, args.suite_timeout)
    if not args.proof_scheme_id and not args.skip_verifier:
        overview = platform.json("GET", "/management/v1/proof-schemas/overview") or {}
        entries = overview.get("proofSchemeDetails", []) if isinstance(overview, dict) else []
        for entry in entries:
            if entry.get("title") == os.getenv("HEIDI_CONFORMANCE_PROOF_TITLE", "OIDF Test PID presentation"):
                args.proof_scheme_id = str(entry["uuid"])
                break
        if not args.proof_scheme_id:
            raise RuntimeError("No seeded proof schema found; set HEIDI_CONFORMANCE_PROOF_SCHEME_ID")
    all_results: list[dict[str, Any]] = []
    if not args.skip_issuer:
        all_results += run_plan(
            suite,
            platform,
            role="issuer",
            plan_name=ISSUER_PLAN,
            variant=ISSUER_VARIANT,
            config_path=args.issuer_plan,
            output_dir=args.output_dir / "issuer",
            args=args,
        )
    if not args.skip_verifier:
        all_results += run_plan(
            suite,
            platform,
            role="verifier",
            plan_name=VERIFIER_PLAN,
            variant=VERIFIER_VARIANT,
            config_path=args.verifier_plan,
            output_dir=args.output_dir / "verifier",
            args=args,
        )
    summary = {"results": all_results}
    (args.output_dir / "summary.json").write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
    failures = [
        result
        for result in all_results
        if result["result"] != PASS_RESULT
        and result["result"] != SKIP_RESULT
        and result["result"] != REVIEW_RESULT
    ]
    if failures:
        print(f"OIDF conformance failed: {json.dumps(failures)}", file=sys.stderr)
        return 1
    print("OIDF conformance passed", flush=True)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as error:  # noqa: BLE001 - concise CI failure
        print(f"OIDF conformance runner failed: {error}", file=sys.stderr)
        raise SystemExit(1)
