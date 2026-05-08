#!/usr/bin/env python3
"""Rebuild app/src/main/assets/databases/ime_database.db to schema v3
with corpus weights seeded from 85rest01.csv and 85rest02.csv.

Idempotent: detects whether the DB is already at v3 and updates weights
only. Run from the RakuRakuIME repo root.
"""
from __future__ import annotations
import csv
import shutil
import sqlite3
import sys
from pathlib import Path

DB = Path("app/src/main/assets/databases/ime_database.db")
BACKUP = DB.with_suffix(".db.bak")
CHAR_CSV = Path("app/src/main/assets/85rest01.csv")
PHRASE_CSV = Path("app/src/main/assets/85rest02.csv")

# Must match FrequencyCsv.SEED_NUMERATOR
SEED_NUMERATOR = 10_000

# Must match schemas/3.json identityHash for ImeDatabase v3.
V3_IDENTITY_HASH = "81978a479ee61b1b72903509f974924b"


def load_weights(csv_path: Path) -> dict[str, int]:
    """Parse a MOE 字頻／詞頻 CSV and return {key: weight}.

    weight = SEED_NUMERATOR // rank; entries with weight 0 are dropped.
    Stops on the first zero-weight row because the CSV is rank-sorted.
    """
    out: dict[str, int] = {}
    with csv_path.open(encoding="utf-8") as f:
        rdr = csv.reader(f)
        for row in rdr:
            if len(row) < 2:
                continue
            try:
                rank = int(row[0].strip())
            except ValueError:
                continue  # header
            key = row[1].strip()
            if not key:
                continue
            weight = SEED_NUMERATOR // rank if rank > 0 else 0
            if weight == 0:
                break
            out.setdefault(key, weight)
    return out


def main() -> int:
    if not DB.exists():
        print(f"error: {DB} not found — run from RakuRakuIME repo root", file=sys.stderr)
        return 2

    # Make a one-shot backup so we can recover if something goes wrong.
    shutil.copyfile(DB, BACKUP)
    print(f"backup -> {BACKUP}")

    print("loading weights...")
    char_w = load_weights(CHAR_CSV)
    phrase_w = load_weights(PHRASE_CSV)
    print(f"  {len(char_w):>5} char weights")
    print(f"  {len(phrase_w):>5} phrase weights")

    con = sqlite3.connect(DB)
    cur = con.cursor()
    user_ver = cur.execute("PRAGMA user_version").fetchone()[0]
    print(f"current user_version: {user_ver}")

    if user_ver == 2:
        print("applying ALTER TABLE (MIGRATION_2_3) ...")
        cur.execute(
            "ALTER TABLE `dictionary` "
            "ADD COLUMN `character_weight` INTEGER NOT NULL DEFAULT 0"
        )
        cur.execute(
            "ALTER TABLE `dictionary` "
            "ADD COLUMN `phrase_weight` INTEGER NOT NULL DEFAULT 0"
        )

    # Reset all weights so a re-run produces deterministic results.
    cur.execute("UPDATE `dictionary` SET character_weight = 0, phrase_weight = 0")

    # Bulk update via temporary keyed tables — vastly faster than per-row
    # UPDATEs for ~10k characters / phrases.
    cur.execute(
        "CREATE TEMP TABLE _char_w "
        "(`key` TEXT PRIMARY KEY NOT NULL, weight INTEGER NOT NULL) WITHOUT ROWID"
    )
    cur.executemany("INSERT INTO _char_w VALUES (?, ?)", char_w.items())
    cur.execute(
        "CREATE TEMP TABLE _phrase_w "
        "(`key` TEXT PRIMARY KEY NOT NULL, weight INTEGER NOT NULL) WITHOUT ROWID"
    )
    cur.executemany("INSERT INTO _phrase_w VALUES (?, ?)", phrase_w.items())

    print("updating character_weight (single-char rows)...")
    # SQLite length() on TEXT counts characters, not bytes — so a single
    # CJK char has length 1.
    n = cur.execute(
        """
        UPDATE `dictionary`
        SET character_weight = (
            SELECT weight FROM _char_w WHERE _char_w.`key` = `dictionary`.character
        )
        WHERE length(`character`) = 1
          AND `character` IN (SELECT `key` FROM _char_w)
        """
    ).rowcount
    print(f"  {n} rows updated")

    print("updating phrase_weight (multi-char rows)...")
    n = cur.execute(
        """
        UPDATE `dictionary`
        SET phrase_weight = (
            SELECT weight FROM _phrase_w WHERE _phrase_w.`key` = `dictionary`.character
        )
        WHERE length(`character`) > 1
          AND `character` IN (SELECT `key` FROM _phrase_w)
        """
    ).rowcount
    print(f"  {n} rows updated")

    print(f"setting user_version = 3 and identity_hash = {V3_IDENTITY_HASH}")
    cur.execute("PRAGMA user_version = 3")
    cur.execute(
        "UPDATE `room_master_table` SET identity_hash = ? WHERE id = 42",
        (V3_IDENTITY_HASH,),
    )

    cur.execute("DROP TABLE _char_w")
    cur.execute("DROP TABLE _phrase_w")

    con.commit()
    print("VACUUM ...")
    cur.execute("VACUUM")
    con.close()

    # Spot checks.
    con = sqlite3.connect(DB)
    cur = con.cursor()
    seeded_chars = cur.execute(
        "SELECT COUNT(DISTINCT `character`) FROM `dictionary` WHERE character_weight > 0"
    ).fetchone()[0]
    seeded_phrases = cur.execute(
        "SELECT COUNT(DISTINCT `character`) FROM `dictionary` WHERE phrase_weight > 0"
    ).fetchone()[0]
    print(f"\ndistinct seeded chars   : {seeded_chars}")
    print(f"distinct seeded phrases : {seeded_phrases}")

    sample = cur.execute(
        "SELECT `character`, character_weight FROM `dictionary` "
        "WHERE `character` IN ('的','一','是','八','輕','鬆') "
        "GROUP BY `character` ORDER BY character_weight DESC"
    ).fetchall()
    print("\nspot-check chars:")
    for c, w in sample:
        print(f"  {c} -> {w}")

    sample = cur.execute(
        "SELECT `character`, phrase_weight FROM `dictionary` "
        "WHERE `character` IN ('輕鬆','輕便','輕易','輕信','輕忽') "
        "GROUP BY `character` ORDER BY phrase_weight DESC"
    ).fetchall()
    print("\nspot-check phrases:")
    for c, w in sample:
        print(f"  {c} -> {w}")
    con.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
