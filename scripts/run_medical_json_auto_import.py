#!/usr/bin/env python3
"""Run medical.json auto import once, with filters driven by environment variables."""

from __future__ import annotations

import os
import shlex
import subprocess
import sys
from pathlib import Path


DEFAULT_TIMEOUT_SECONDS = 600
DEFAULT_WAIT_INTERVAL_SECONDS = 5


def main() -> int:
    if not read_bool("MEDICAL_JSON_AUTO_IMPORT_ENABLED", True):
        print("medical.json 自动导入已禁用，跳过。")
        return 0

    repo_root = Path(__file__).resolve().parent.parent
    script_path = Path(__file__).with_name("import_medical_json_dataset.py")
    dataset_path = os.getenv("MEDICAL_JSON_AUTO_IMPORT_DATASET", str(repo_root / "medical.json"))
    output_root = os.getenv("MEDICAL_JSON_AUTO_IMPORT_OUTPUT_ROOT", "/tmp/medical_json_import")
    api_base_url = os.getenv("MEDICAL_JSON_AUTO_IMPORT_API_BASE_URL", "http://cdm-backend:8080")

    command = [
        sys.executable,
        str(script_path),
        "--dataset",
        dataset_path,
        "--output-root",
        output_root,
        "--api-base-url",
        api_base_url,
        "--wait-for-api-seconds",
        str(read_int("MEDICAL_JSON_AUTO_IMPORT_WAIT_TIMEOUT", DEFAULT_TIMEOUT_SECONDS)),
        "--wait-interval-seconds",
        str(read_int("MEDICAL_JSON_AUTO_IMPORT_WAIT_INTERVAL", DEFAULT_WAIT_INTERVAL_SECONDS)),
        "--upload",
    ]

    if read_bool("MEDICAL_JSON_AUTO_IMPORT_INCLUDE_GENERAL", False):
        command.append("--include-general")

    for disease in split_values(os.getenv("MEDICAL_JSON_AUTO_IMPORT_DISEASES", "")):
        command.extend(["--disease", disease])

    max_records = read_int("MEDICAL_JSON_AUTO_IMPORT_MAX_RECORDS", 0)
    if max_records > 0:
        command.extend(["--max-records", str(max_records)])

    for keyword in split_values(os.getenv("MEDICAL_JSON_AUTO_IMPORT_KEYWORDS", "")):
        command.extend(["--keyword", keyword])

    keyword_field = os.getenv("MEDICAL_JSON_AUTO_IMPORT_KEYWORD_FIELD", "name").strip()
    if keyword_field:
        command.extend(["--keyword-field", keyword_field])

    for keyword in split_values(os.getenv("MEDICAL_JSON_AUTO_IMPORT_EXCLUDE_KEYWORDS", "")):
        command.extend(["--exclude-keyword", keyword])

    exclude_keyword_field = os.getenv("MEDICAL_JSON_AUTO_IMPORT_EXCLUDE_KEYWORD_FIELD", "name").strip()
    if exclude_keyword_field:
        command.extend(["--exclude-keyword-field", exclude_keyword_field])

    force_disease = os.getenv("MEDICAL_JSON_AUTO_IMPORT_FORCE_DISEASE", "").strip()
    if force_disease:
        command.extend(["--force-disease", force_disease])

    published_at = os.getenv("MEDICAL_JSON_AUTO_IMPORT_PUBLISHED_AT", "").strip()
    if published_at:
        command.extend(["--published-at", published_at])

    version_tag = os.getenv("MEDICAL_JSON_AUTO_IMPORT_VERSION_TAG", "medical-json").strip()
    if version_tag:
        command.extend(["--version-tag", version_tag])

    auth_username = (
        os.getenv("MEDICAL_JSON_AUTO_IMPORT_USERNAME", "").strip()
        or os.getenv("INITIAL_ADMIN_USERNAME", "").strip()
    )
    auth_password = (
        os.getenv("MEDICAL_JSON_AUTO_IMPORT_PASSWORD", "").strip()
        or os.getenv("INITIAL_ADMIN_PASSWORD", "").strip()
    )
    auth_role = os.getenv("MEDICAL_JSON_AUTO_IMPORT_AUTH_ROLE", "admin").strip() or "admin"
    if auth_username:
        command.extend(["--auth-username", auth_username])
    if auth_password:
        command.extend(["--auth-password", auth_password])
    if auth_role:
        command.extend(["--auth-role", auth_role])

    print("执行 medical.json 自动导入:")
    redacted_command = []
    skip_next = False
    for index, part in enumerate(command):
        if skip_next:
            redacted_command.append("******")
            skip_next = False
            continue
        redacted_command.append(part)
        if part == "--auth-password" and index + 1 < len(command):
            skip_next = True
    command.append("--rebuild-graph")
    print(shlex.join(redacted_command + ["--rebuild-graph"]))
    completed = subprocess.run(command, check=False)
    return completed.returncode


def split_values(raw: str) -> list[str]:
    if not raw:
        return []
    normalized = raw.replace("\r", "\n").replace(";", ",").replace("\n", ",")
    return [item.strip() for item in normalized.split(",") if item.strip()]


def read_bool(name: str, default: bool) -> bool:
    value = os.getenv(name)
    if value is None or not value.strip():
        return default
    return value.strip().lower() in {"1", "true", "yes", "on"}


def read_int(name: str, default: int) -> int:
    value = os.getenv(name)
    if value is None or not value.strip():
        return default
    try:
        return int(value.strip())
    except ValueError:
        return default


if __name__ == "__main__":
    raise SystemExit(main())
