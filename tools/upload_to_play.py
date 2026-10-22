#!/usr/bin/env python3
"""Upload Replicant303 to Google Play Store using the Google Play Developer API.

Requires:
  - Service Account JSON key (gen-lang-client-*.json)
  - Google Play Android Developer API enabled in Google Cloud Console
  - Service Account added to Google Play Console with Release Manager permissions
"""

from __future__ import annotations

import argparse
import glob
import json
import os
import sys
from pathlib import Path

from google.oauth2 import service_account
from googleapiclient.discovery import build
from googleapiclient.http import MediaFileUpload

ROOT = Path(__file__).resolve().parent.parent
PACKAGE_NAME = "com.danimanx.replicant303"
SCOPES = ["https://www.googleapis.com/auth/androidpublisher"]


def find_key_file() -> Path:
    candidates = list(ROOT.glob("gen-lang-client*.json")) + list(ROOT.glob("*service_account*.json"))
    if not candidates:
        raise FileNotFoundError("No service account JSON found in project root.")
    return candidates[0]


def extract_listing(path: Path) -> dict[str, str]:
    content = path.read_text(encoding="utf-8")
    import re
    headings = list(re.finditer(r"^##\s+(.+?)\s*$", content, re.M))
    fences = list(re.finditer(r"```[a-zA-Z]*\n(.*?)```", content, re.S))
    data = {}
    for h_match, f_match in zip(headings, fences):
        h = h_match.group(1).lower()
        body = f_match.group(1).strip()
        if "app name" in h or "nombre de la app" in h:
            data["title"] = body
        elif "short description" in h or "descripción corta" in h:
            data["shortDescription"] = body
        elif "full description" in h or "descripción completa" in h:
            data["fullDescription"] = body
    return data


def main() -> int:
    parser = argparse.ArgumentParser(description="Upload Replicant303 to Google Play Store.")
    parser.add_argument("--track", default="internal", choices=["internal", "alpha", "beta", "production"],
                        help="Release track (default: internal)")
    parser.add_argument("--status", default="completed", choices=["completed", "draft", "inProgress", "halted"],
                        help="Release status on the track (default: completed)")
    parser.add_argument("--key", type=Path, default=None, help="Path to service account JSON key")
    parser.add_argument("--aab", type=Path, default=ROOT / "dist" / "Replicant303-Android-1.0.1.aab",
                        help="Path to release AAB bundle")
    parser.add_argument("--dry-run", action="store_true", help="Validate credentials and files without committing")
    args = parser.parse_args()

    key_path = args.key or find_key_file()
    print(f"Using service account key: {key_path.name}")
    print(f"Target AAB: {args.aab}")
    print(f"Package: {PACKAGE_NAME}")
    print(f"Track: {args.track} ({args.status})")

    if not args.aab.is_file():
        print(f"Error: AAB file not found at {args.aab}", file=sys.stderr)
        return 1

    creds = service_account.Credentials.from_service_account_file(str(key_path), scopes=SCOPES)
    service = build("androidpublisher", "v3", credentials=creds)

    print("\n1. Inserting new edit...")
    edit_request = service.edits().insert(body={}, packageName=PACKAGE_NAME)
    edit = edit_request.execute()
    edit_id = edit["id"]
    print(f"Edit created with ID: {edit_id}")

    try:
        print("\n2. Uploading App Bundle (.aab)...")
        bundle_media = MediaFileUpload(str(args.aab), mimetype="application/octet-stream", resumable=True)
        bundle_res = service.edits().bundles().upload(
            packageName=PACKAGE_NAME, editId=edit_id, media_body=bundle_media
        ).execute()
        version_code = bundle_res["versionCode"]
        print(f"Uploaded AAB successfully! Version Code: {version_code}")

        # 3. Store listings
        print("\n3. Updating Store Listings...")
        listings_dir = ROOT / "store" / "listing"
        for listing_file in sorted(listings_dir.glob("*.md")):
            lang = listing_file.stem  # e.g. es-419, en-US
            data = extract_listing(listing_file)
            if data:
                print(f"  Setting listing for {lang}: '{data.get('title')}'")
                service.edits().listings().update(
                    packageName=PACKAGE_NAME, editId=edit_id, language=lang, body=data
                ).execute()

        # 4. Store Graphics
        print("\n4. Uploading Store Graphics...")
        icon_path = ROOT / "store" / "icon-512.png"
        feature_path = ROOT / "store" / "feature-graphic-1024x500.png"
        for lang in ["es-419", "en-US"]:
            if icon_path.is_file():
                print(f"  Uploading icon for {lang}...")
                service.edits().images().upload(
                    packageName=PACKAGE_NAME, editId=edit_id, language=lang, imageType="icon",
                    media_body=MediaFileUpload(str(icon_path), mimetype="image/png")
                ).execute()
            if feature_path.is_file():
                print(f"  Uploading feature graphic for {lang}...")
                service.edits().images().upload(
                    packageName=PACKAGE_NAME, editId=edit_id, language=lang, imageType="featureGraphic",
                    media_body=MediaFileUpload(str(feature_path), mimetype="image/png")
                ).execute()

            # Screenshots
            shots = sorted((ROOT / "store" / "screenshots").glob("phone_*.png"))[:8]
            for shot in shots:
                print(f"  Uploading phone screenshot {shot.name} for {lang}...")
                service.edits().images().upload(
                    packageName=PACKAGE_NAME, editId=edit_id, language=lang, imageType="phoneScreenshots",
                    media_body=MediaFileUpload(str(shot), mimetype="image/png")
                ).execute()

            tablet_shots = sorted((ROOT / "store" / "screenshots").glob("tablet_*.png"))
            for shot in tablet_shots:
                print(f"  Uploading tablet screenshot {shot.name} for {lang}...")
                service.edits().images().upload(
                    packageName=PACKAGE_NAME, editId=edit_id, language=lang, imageType="sevenInchScreenshots",
                    media_body=MediaFileUpload(str(shot), mimetype="image/png")
                ).execute()
                service.edits().images().upload(
                    packageName=PACKAGE_NAME, editId=edit_id, language=lang, imageType="tenInchScreenshots",
                    media_body=MediaFileUpload(str(shot), mimetype="image/png")
                ).execute()

        # 5. Release track & notes
        print(f"\n5. Assigning version {version_code} to track '{args.track}'...")
        release_notes = []
        for lang_code, folder in [("es-419", "es-419"), ("en-US", "en-US")]:
            notes_file = ROOT / "store" / "release-notes" / folder / "default.txt"
            if notes_file.is_file():
                release_notes.append({
                    "language": lang_code,
                    "text": notes_file.read_text(encoding="utf-8").strip()
                })

        track_body = {
            "track": args.track,
            "releases": [{
                "name": f"1.0.1 ({version_code})",
                "versionCodes": [str(version_code)],
                "status": args.status,
                "releaseNotes": release_notes,
            }]
        }
        service.edits().tracks().update(
            packageName=PACKAGE_NAME, editId=edit_id, track=args.track, body=track_body
        ).execute()

        if args.dry_run:
            print("\nDry run requested: Not committing edit.")
            service.edits().delete(packageName=PACKAGE_NAME, editId=edit_id).execute()
            print("Edit rolled back cleanly.")
            return 0

        # 6. Commit edit
        print("\n6. Committing release to Google Play...")
        commit_res = service.edits().commit(packageName=PACKAGE_NAME, editId=edit_id).execute()
        print(f"SUCCESS! Edit committed: {commit_res}")
        print(f"Replicant303 (version {version_code}) is now uploaded to Google Play track '{args.track}'!")
        return 0

    except Exception as e:
        print(f"\nError during edit execution: {e}", file=sys.stderr)
        try:
            service.edits().delete(packageName=PACKAGE_NAME, editId=edit_id).execute()
            print("Cleaned up uncommitted edit.")
        except Exception:
            pass
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
