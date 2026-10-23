#!/usr/bin/env python3
"""Convert medical.json (JSONL disease encyclopedia) into knowledge-upload documents."""

from __future__ import annotations

import argparse
import csv
import json
import mimetypes
import sys
import time
import uuid
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, Iterator, List, Sequence
from urllib import error, request


DEFAULT_DATASET = Path("medical.json")
DEFAULT_OUTPUT_ROOT = Path("scripts/generated/medical_json_import")
DEFAULT_VERSION_TAG = "medical-json"

DISEASE_RULES: Sequence[tuple[str, Sequence[str]]] = (
    ("高血压", ("高血压", "血压", "收缩压", "舒张压", "高血压病", "hypertension")),
    ("糖尿病", ("糖尿病", "血糖", "胰岛素", "糖化血红蛋白", "diabetes", "glucose")),
    ("慢阻肺", ("慢阻肺", "copd", "慢性阻塞性肺疾病", "肺气肿", "肺功能", "呼吸困难")),
    ("慢性肾病", ("慢性肾病", "慢性肾脏病", "ckd", "肾病", "肾功能", "肌酐", "尿蛋白")),
    ("冠心病", ("冠心病", "心绞痛", "冠状动脉", "心肌缺血", "胸痛", "chd", "cad")),
    ("脑卒中", ("脑卒中", "卒中", "中风", "脑梗", "脑出血", "stroke")),
)

SECTION_LABELS: Sequence[tuple[str, str]] = (
    ("desc", "疾病简介"),
    ("prevent", "预防"),
    ("cause", "病因"),
    ("symptom", "症状"),
    ("acompany", "并发症"),
    ("check", "检查"),
    ("cure_way", "治疗方式"),
    ("cure_department", "就诊科室"),
    ("cure_lasttime", "治疗周期"),
    ("cured_prob", "治愈概率"),
    ("common_drug", "常用药物"),
    ("recommand_drug", "推荐药物"),
    ("drug_detail", "药品明细"),
    ("do_eat", "宜吃"),
    ("not_eat", "忌吃"),
    ("recommand_eat", "推荐食谱"),
    ("easy_get", "易感人群"),
    ("get_way", "传播方式"),
    ("get_prob", "发病概率"),
    ("cost_money", "治疗费用"),
    ("yibao_status", "医保状态"),
)

TAG_RULES: Sequence[tuple[str, Sequence[str]]] = (
    ("饮食", ("do_eat", "not_eat", "recommand_eat")),
    ("用药", ("common_drug", "recommand_drug", "drug_detail")),
    ("并发症预防", ("prevent", "acompany")),
    ("监测指标", ("check", "symptom")),
)
KEYWORD_FIELDS = ("name", "category", "desc", "all")


@dataclass
class Record:
    name: str
    disease: str
    title: str
    source: str
    content_tags: str
    text: str


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Convert medical.json into knowledge-upload documents for the chronic disease knowledge base."
    )
    parser.add_argument("--dataset", default=str(DEFAULT_DATASET), help="Path to medical.json (JSONL).")
    parser.add_argument("--output-root", default=str(DEFAULT_OUTPUT_ROOT), help="Directory for generated docs and manifest.")
    parser.add_argument(
        "--include-general",
        action="store_true",
        help="Include unmatched entries and label them as 通用. Default skips them.",
    )
    parser.add_argument(
        "--disease",
        action="append",
        default=[],
        choices=["高血压", "糖尿病", "慢阻肺", "慢性肾病", "冠心病", "脑卒中", "通用"],
        help="Only export the selected disease label(s). Can be repeated.",
    )
    parser.add_argument(
        "--max-records",
        type=int,
        default=0,
        help="Optional total record cap after filtering. 0 means no limit.",
    )
    parser.add_argument(
        "--keyword",
        action="append",
        default=[],
        help="Only include entries whose selected field contains this keyword. Can be repeated.",
    )
    parser.add_argument(
        "--keyword-field",
        default="name",
        choices=KEYWORD_FIELDS,
        help="Which field is checked by --keyword. Default: %(default)s",
    )
    parser.add_argument(
        "--exclude-keyword",
        action="append",
        default=[],
        help="Exclude entries whose selected field contains this keyword. Can be repeated.",
    )
    parser.add_argument(
        "--exclude-keyword-field",
        default="name",
        choices=KEYWORD_FIELDS,
        help="Which field is checked by --exclude-keyword. Default: %(default)s",
    )
    parser.add_argument(
        "--force-disease",
        default="",
        choices=["", "高血压", "糖尿病", "慢阻肺", "慢性肾病", "冠心病", "脑卒中", "通用"],
        help="Force every exported document to use one project disease label.",
    )
    parser.add_argument(
        "--published-at",
        default="",
        help="Optional YYYY-MM-DD publishedAt value written into the manifest.",
    )
    parser.add_argument(
        "--version-tag",
        default=DEFAULT_VERSION_TAG,
        help="versionTag written into the manifest. Default: %(default)s",
    )
    parser.add_argument(
        "--api-base-url",
        default="http://localhost:8080",
        help="Knowledge upload API base URL used when --upload is set. Default: %(default)s",
    )
    parser.add_argument(
        "--wait-for-api-seconds",
        type=int,
        default=0,
        help="Wait until the knowledge API becomes available before uploading. 0 disables waiting.",
    )
    parser.add_argument(
        "--wait-interval-seconds",
        type=int,
        default=5,
        help="Polling interval used with --wait-for-api-seconds. Default: %(default)s",
    )
    parser.add_argument(
        "--upload",
        action="store_true",
        help="Upload the generated manifest immediately by calling the knowledge upload API.",
    )
    parser.add_argument("--auth-username", default="", help="Optional username used to login before upload/rebuild.")
    parser.add_argument("--auth-password", default="", help="Optional password used to login before upload/rebuild.")
    parser.add_argument("--auth-role", default="admin", help="Role sent to /api/auth/login. Default: %(default)s")
    parser.add_argument(
        "--rebuild-graph",
        action="store_true",
        help="Rebuild the knowledge graph after a successful upload.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    dataset_path = Path(args.dataset).resolve()
    output_root = Path(args.output_root).resolve()
    docs_root = output_root / "docs"
    manifest_path = output_root / "manifest.csv"

    if not dataset_path.exists():
        raise SystemExit(f"数据集文件不存在: {dataset_path}")

    selected = set(args.disease)
    include_general = args.include_general or "通用" in selected
    docs_root.mkdir(parents=True, exist_ok=True)

    records = list(
        iter_records(
            dataset_path=dataset_path,
            selected_diseases=selected,
            include_general=include_general,
            max_records=args.max_records,
            keywords=[item.strip() for item in args.keyword if item.strip()],
            keyword_field=args.keyword_field,
            exclude_keywords=[item.strip() for item in args.exclude_keyword if item.strip()],
            exclude_keyword_field=args.exclude_keyword_field,
            force_disease=args.force_disease.strip(),
        )
    )
    manifest_rows = write_docs(
        records=records,
        docs_root=docs_root,
        published_at=args.published_at.strip(),
        version_tag=args.version_tag.strip(),
    )
    write_manifest(manifest_path, manifest_rows)

    print(f"生成完成: docs={len(manifest_rows)} manifest={manifest_path}")
    if manifest_rows:
        stats = summarize(records)
        for disease, total in stats.items():
            print(f"{disease}: {total}")

    if args.upload:
        wait_for_api(args.api_base_url, args.wait_for_api_seconds, args.wait_interval_seconds)
        auth_headers = build_auth_headers(
            api_base_url=args.api_base_url,
            username=args.auth_username.strip(),
            password=args.auth_password.strip(),
            role=args.auth_role.strip() or "admin",
        )
        wait_for_api(args.api_base_url, args.wait_for_api_seconds, args.wait_interval_seconds, auth_headers)
        upload_status = run_upload(manifest_path, args.api_base_url, auth_headers)
        if upload_status != 0:
            return upload_status
        if args.rebuild_graph:
            return rebuild_graph(args.api_base_url, auth_headers)
    return 0


def iter_records(
    dataset_path: Path,
    selected_diseases: set[str],
    include_general: bool,
    max_records: int,
    keywords: Sequence[str],
    keyword_field: str,
    exclude_keywords: Sequence[str],
    exclude_keyword_field: str,
    force_disease: str,
) -> Iterator[Record]:
    emitted = 0
    with dataset_path.open("r", encoding="utf-8") as handle:
        for line_number, line in enumerate(handle, start=1):
            line = line.strip()
            if not line:
                continue
            try:
                item = json.loads(line)
            except json.JSONDecodeError as exc:
                raise SystemExit(f"第 {line_number} 行 JSON 解析失败: {exc}") from exc

            if keywords and not matches_keywords(item, keywords, keyword_field):
                continue

            if exclude_keywords and matches_keywords(item, exclude_keywords, exclude_keyword_field):
                continue

            disease = force_disease or infer_disease(item)
            if disease == "通用" and not include_general:
                continue
            if selected_diseases and disease not in selected_diseases:
                continue

            record = build_record(item, disease)
            if not record:
                continue

            yield record
            emitted += 1
            if max_records > 0 and emitted >= max_records:
                break


def infer_disease(item: Dict[str, object]) -> str:
    name = normalize_text(item.get("name")).lower()
    category = normalize_text(item.get("category")).lower()
    desc = normalize_text(item.get("desc"))[:120].lower()
    best_label = "通用"
    best_score = 0
    for label, keywords in DISEASE_RULES:
        score = 0
        for keyword in keywords:
            token = keyword.lower()
            if token in name:
                score += 6
            if token in category:
                score += 3
            if token in desc:
                score += 1
        if score > best_score:
            best_score = score
            best_label = label

    return best_label if best_score >= 6 else "通用"


def matches_keywords(item: Dict[str, object], keywords: Sequence[str], keyword_field: str) -> bool:
    haystack = build_keyword_haystack(item, keyword_field)
    if not haystack:
        return False
    lowered = haystack.lower()
    return any(keyword.lower() in lowered for keyword in keywords)


def build_keyword_haystack(item: Dict[str, object], keyword_field: str) -> str:
    if keyword_field == "name":
        return normalize_text(item.get("name"))
    if keyword_field == "category":
        return normalize_text(item.get("category"))
    if keyword_field == "desc":
        return normalize_text(item.get("desc"))
    parts = [
        normalize_text(item.get("name")),
        normalize_text(item.get("category")),
        normalize_text(item.get("desc")),
        normalize_text(item.get("cause")),
        normalize_text(item.get("prevent")),
    ]
    return "\n".join(part for part in parts if part)


def build_record(item: Dict[str, object], disease: str) -> Record | None:
    name = normalize_text(item.get("name"))
    if not name:
        return None

    sections: List[str] = [f"# {name}", f"核心病种：{disease}"]
    categories = normalize_text(item.get("category"))
    if categories:
        sections.append(f"原始分类：{categories}")
    sections.append("")

    present_tags: List[str] = []
    for tag, fields in TAG_RULES:
        if any(normalize_text(item.get(field)) for field in fields):
            present_tags.append(tag)

    for field, label in SECTION_LABELS:
        value = normalize_text(item.get(field))
        if not value:
            continue
        sections.extend([f"# {label}", value, ""])

    text = "\n".join(sections).strip() + "\n"
    return Record(
        name=name,
        disease=disease,
        title=f"{name}-{disease}-疾病知识",
        source="medical.json",
        content_tags=",".join(present_tags),
        text=text,
    )


def normalize_text(value: object) -> str:
    if value is None:
        return ""
    if isinstance(value, list):
        parts = [normalize_text(item) for item in value]
        return "；".join(part for part in parts if part)
    if isinstance(value, dict):
        parts = [normalize_text(item) for item in value.values()]
        return "；".join(part for part in parts if part)
    text = str(value).replace("\r\n", "\n").replace("\r", "\n").strip()
    return "\n".join(line.strip() for line in text.split("\n") if line.strip())


def write_docs(records: Sequence[Record], docs_root: Path, published_at: str, version_tag: str) -> List[Dict[str, str]]:
    manifest_rows: List[Dict[str, str]] = []
    for index, record in enumerate(records, start=1):
        relative_doc = Path("docs") / f"doc-{index:06d}.txt"
        absolute_doc = docs_root / relative_doc.name
        absolute_doc.write_text(record.text, encoding="utf-8")
        manifest_rows.append(
            {
                "file": str(relative_doc).replace("\\", "/"),
                "disease": record.disease,
                "title": record.title,
                "source": record.source,
                "publishedAt": published_at,
                "versionTag": version_tag,
                "contentTags": record.content_tags,
            }
        )
    return manifest_rows


def write_manifest(manifest_path: Path, manifest_rows: Sequence[Dict[str, str]]) -> None:
    manifest_path.parent.mkdir(parents=True, exist_ok=True)
    with manifest_path.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(
            handle,
            fieldnames=["file", "disease", "title", "source", "publishedAt", "versionTag", "contentTags"],
        )
        writer.writeheader()
        for row in manifest_rows:
            writer.writerow(row)


def summarize(records: Sequence[Record]) -> Dict[str, int]:
    stats: Dict[str, int] = {}
    for record in records:
        stats[record.disease] = stats.get(record.disease, 0) + 1
    return dict(sorted(stats.items(), key=lambda item: item[0]))


def wait_for_api(api_base_url: str, timeout_seconds: int, interval_seconds: int, auth_headers: Dict[str, str] | None = None) -> None:
    if timeout_seconds <= 0:
        return

    status_url = (
        api_base_url.rstrip("/") + ("/api/knowledge/status" if auth_headers else "/api/auth/config")
    )
    deadline = time.monotonic() + timeout_seconds
    interval = max(1, interval_seconds)
    last_error = ""

    while time.monotonic() < deadline:
        try:
            http_request = request.Request(
                status_url,
                headers=auth_headers or {},
                method="GET",
            )
            with request.urlopen(http_request, timeout=10) as response:
                if 200 <= response.status < 300:
                    print(f"知识库接口已就绪: {status_url}")
                    return
                last_error = f"HTTP {response.status}"
        except Exception as exc:  # pragma: no cover - network timing depends on runtime
            last_error = str(exc)

        print(f"等待知识库接口就绪: {status_url} ({last_error or 'pending'})")
        time.sleep(interval)

    raise SystemExit(f"等待知识库接口超时({timeout_seconds}s): {status_url} {last_error}".strip())


def run_upload(manifest_path: Path, api_base_url: str, auth_headers: Dict[str, str] | None = None) -> int:
    rows = load_manifest_rows(manifest_path)
    if not rows:
        raise SystemExit(f"清单文件为空: {manifest_path}")

    api_url = api_base_url.rstrip("/") + "/api/knowledge/upload"
    failed_count = 0

    for index, row in enumerate(rows, start=1):
        file_name = row.get("file") or ""
        try:
            response = upload_manifest_row(manifest_path, row, api_url, auth_headers)
            index_status = str(response.get("indexStatus") or "UNKNOWN")
            existing_index_status = str(response.get("existingIndexStatus") or "")
            chunk_count = parse_int(response.get("chunkCount"))
            indexed_chunk_count = parse_int(response.get("indexedChunkCount"))
            message = str(response.get("message") or "知识库上传成功")
            suffix = f", 已有索引状态={existing_index_status}" if existing_index_status else ""
            print(
                f"[{index}/{len(rows)}] {file_name} -> {index_status} "
                f"({indexed_chunk_count}/{chunk_count}) {message}{suffix}"
            )
            if index_status in {"FAILED", "PARTIAL", "ERROR"}:
                failed_count += 1
        except Exception as exc:
            failed_count += 1
            target_name = file_name or row.get("title") or "unknown"
            print(f"[{index}/{len(rows)}] {target_name} -> ERROR: {exc}", file=sys.stderr)

    if failed_count > 0:
        print(f"导入完成，但有 {failed_count} 条记录未完全成功。", file=sys.stderr)
        return 1

    print(f"导入完成，共 {len(rows)} 条，全部成功。")
    return 0


def load_manifest_rows(manifest_path: Path) -> List[Dict[str, str]]:
    with manifest_path.open("r", encoding="utf-8-sig", newline="") as handle:
        rows = list(csv.DictReader(handle))
    if not rows:
        return []

    required_columns = {"file", "disease"}
    headers = set(rows[0].keys())
    missing = required_columns - headers
    if missing:
        raise SystemExit(f"清单文件缺少必填列: {', '.join(sorted(missing))}")
    return rows


def upload_manifest_row(
    manifest_path: Path,
    row: Dict[str, str],
    api_url: str,
    auth_headers: Dict[str, str] | None = None,
) -> Dict[str, object]:
    resolved_file = resolve_import_file_path(manifest_path, row.get("file", ""))
    file_name = resolved_file.name
    file_bytes = resolved_file.read_bytes()
    content_type = mimetypes.guess_type(file_name)[0] or "application/octet-stream"

    fields: Dict[str, str] = {"disease": (row.get("disease") or "").strip()}
    if not fields["disease"]:
        raise SystemExit("清单文件中的 disease 不能为空")
    for name in ("title", "source", "publishedAt", "versionTag", "contentTags"):
        value = (row.get(name) or "").strip()
        if value:
            fields[name] = value

    body, boundary = build_multipart_form_data(fields, file_name, file_bytes, content_type)
    headers = {"Content-Type": f"multipart/form-data; boundary={boundary}"}
    if auth_headers:
        headers.update(auth_headers)

    http_request = request.Request(api_url, data=body, headers=headers, method="POST")

    try:
        with request.urlopen(http_request, timeout=600) as response:
            payload = response.read().decode("utf-8", errors="replace")
            return json.loads(payload) if payload else {}
    except error.HTTPError as exc:
        body_text = exc.read().decode("utf-8", errors="replace")
        message = body_text
        if body_text:
            try:
                message = json.loads(body_text).get("message") or body_text
            except json.JSONDecodeError:
                message = body_text
        raise SystemExit(f"HTTP {exc.code}: {message}") from exc


def rebuild_graph(api_base_url: str, auth_headers: Dict[str, str] | None = None) -> int:
    rebuild_url = api_base_url.rstrip("/") + "/api/knowledge-graph/rebuild"
    http_request = request.Request(
        rebuild_url,
        data=b"{}",
        headers={"Content-Type": "application/json", **(auth_headers or {})},
        method="POST",
    )

    try:
        with request.urlopen(http_request, timeout=600) as response:
            payload = response.read().decode("utf-8", errors="replace")
            result = json.loads(payload) if payload else {}
            print(
                "知识图谱重建完成: "
                f"节点 {parse_int(result.get('nodeCount'))}，关系 {parse_int(result.get('edgeCount'))}"
            )
            return 0
    except error.HTTPError as exc:
        body_text = exc.read().decode("utf-8", errors="replace")
        message = body_text
        if body_text:
            try:
                message = json.loads(body_text).get("message") or body_text
            except json.JSONDecodeError:
                message = body_text
        print(f"知识图谱重建失败: HTTP {exc.code}: {message}", file=sys.stderr)
        return 1


def build_auth_headers(api_base_url: str, username: str, password: str, role: str) -> Dict[str, str]:
    if not username or not password:
        return {}

    login_url = api_base_url.rstrip("/") + "/api/auth/login"
    payload = json.dumps({
        "username": username,
        "password": password,
        "role": role or "admin",
    }).encode("utf-8")
    http_request = request.Request(
        login_url,
        data=payload,
        headers={"Content-Type": "application/json"},
        method="POST",
    )

    try:
        with request.urlopen(http_request, timeout=30) as response:
            body = response.read().decode("utf-8", errors="replace")
            result = json.loads(body) if body else {}
            token = str(result.get("token") or "").strip()
            if not token:
                raise SystemExit("登录成功，但响应中缺少 token。")
            print(f"已使用 {role or 'admin'} 账号登录，准备执行知识库导入。")
            return {"Authorization": f"Bearer {token}"}
    except error.HTTPError as exc:
        body_text = exc.read().decode("utf-8", errors="replace")
        message = body_text
        if body_text:
            try:
                message = json.loads(body_text).get("message") or body_text
            except json.JSONDecodeError:
                message = body_text
        raise SystemExit(f"自动导入登录失败: HTTP {exc.code}: {message}") from exc


def resolve_import_file_path(manifest_path: Path, file_path: str) -> Path:
    if not file_path.strip():
        raise SystemExit("manifest 的 file 列不能为空")
    candidate = Path(file_path)
    if not candidate.is_absolute():
        candidate = manifest_path.parent / candidate
    candidate = candidate.resolve()
    if not candidate.exists():
        raise SystemExit(f"找不到导入文件: {candidate}")
    return candidate


def build_multipart_form_data(fields: Dict[str, str],
                              file_name: str,
                              file_bytes: bytes,
                              content_type: str) -> tuple[bytes, str]:
    boundary = "----CodexBoundary" + uuid.uuid4().hex
    boundary_bytes = boundary.encode("utf-8")
    body = bytearray()

    for name, value in fields.items():
        body.extend(b"--" + boundary_bytes + b"\r\n")
        body.extend(f'Content-Disposition: form-data; name="{name}"\r\n\r\n'.encode("utf-8"))
        body.extend(value.encode("utf-8"))
        body.extend(b"\r\n")

    body.extend(b"--" + boundary_bytes + b"\r\n")
    body.extend(f'Content-Disposition: form-data; name="file"; filename="{file_name}"\r\n'.encode("utf-8"))
    body.extend(f"Content-Type: {content_type}\r\n\r\n".encode("utf-8"))
    body.extend(file_bytes)
    body.extend(b"\r\n")
    body.extend(b"--" + boundary_bytes + b"--\r\n")
    return bytes(body), boundary


def parse_int(value: object) -> int:
    if value is None or value == "":
        return 0
    try:
        return int(value)
    except (TypeError, ValueError):
        return 0


if __name__ == "__main__":
    raise SystemExit(main())
