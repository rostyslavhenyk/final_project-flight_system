#!/usr/bin/env python3
"""
Regenerate flight schedule templates into SQLite (data/db/flight_schedule_templates.db),
matching the table shape used by Kotlin (DB-first, CSV fallback).

Optional: pass --csv to also write data/flight_schedule_templates.csv for diffs or tooling.

Re-run after editing airport lists (CODES / regions).
"""

from __future__ import annotations

import argparse
import csv
import sqlite3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CSV_OUT = ROOT / "data" / "flight_schedule_templates.csv"
DB_OUT = ROOT / "data" / "db" / "flight_schedule_templates.db"

# Must match data/airports.csv (City (XXX)) and data/airports_display.csv
CODES = [
    "MAN",
    "LBA",
    "LHR",
    "LGW",
    "EDI",
    "HKG",
    "BKK",
    "DPS",
    "YVR",
    "FCO",
    "SIN",
    "DXB",
    "LAX",
    "JFK",
    "CDG",
    "BCN",
    "AMS",
    "NRT",
    "SYD",
    "ICN",
    "IST",
    "DOH",
    "FRA",
    "MUC",
    "ZRH",
    "KUL",
    "CPH",
    "DEL",
    "AUH",
]

EU_UK = {
    "MAN",
    "LBA",
    "LHR",
    "LGW",
    "EDI",
    "AMS",
    "CDG",
    "BCN",
    "FCO",
    "IST",
    "FRA",
    "MUC",
    "ZRH",
    "CPH",
}
ASIA_PAC = {"HKG", "BKK", "SIN", "DPS", "NRT", "SYD", "ICN", "KUL", "DEL"}
NA = {"YVR", "LAX", "JFK"}
ME = {"DXB", "DOH", "AUH"}

# One-stop long-haul hub leg patterns (local times, arrival day offset last leg)
# (dep1, arr1, dep2, arr2, arrival_offset_days) — rough, plausible clock times
HUB_1STOP = {
    "DXB": ("09:40", "19:10", "23:10", "15:10", 1),
    "DOH": ("10:15", "19:40", "23:35", "15:40", 1),
    "IST": ("08:50", "18:25", "22:50", "09:15", 1),
    "AUH": ("10:05", "19:20", "23:20", "15:25", 1),
    "SIN": ("11:20", "07:05", "12:40", "16:50", 1),
    "HKG": ("12:05", "07:40", "14:20", "17:35", 1),
    "BKK": ("09:55", "06:10", "13:25", "12:40", 1),
    "KUL": ("10:40", "06:55", "14:10", "13:20", 1),
    "LHR": ("07:45", "09:30", "21:15", "17:25", 1),
    "AMS": ("08:10", "09:55", "20:40", "16:50", 1),
    "DEL": ("11:30", "23:05", "02:15", "12:30", 1),
}

TWO_STOP_PATTERNS = [
    ("LHR", "BKK"),
    ("AMS", "DXB"),
    ("IST", "SIN"),
    ("CDG", "DOH"),
    ("FRA", "HKG"),
    ("MUC", "SIN"),
    ("ZRH", "DXB"),
    ("CPH", "AMS"),
    ("DOH", "BKK"),
    ("DXB", "HKG"),
]


def snap5(m: int) -> int:
    return (m // 5) * 5


def parse_t(s: str) -> int:
    h, mm = s.split(":")
    return int(h) * 60 + int(mm)


def fmt_t(total_min: int) -> str:
    total_min %= 24 * 60
    h, m = divmod(snap5(total_min), 60)
    return f"{h}:{m:02d}"


def shift_clock(s: str, delta_min: int) -> str:
    return fmt_t(parse_t(s) + delta_min)


def pick_1stop_hubs(o: str, d: str) -> list[str]:
    """Diverse hubs; never equals origin or destination."""
    pool = ["DXB", "DOH", "IST", "SIN", "HKG", "AUH", "KUL", "DEL", "LHR", "AMS", "BKK"]
    out = []
    for h in pool:
        if h == o or h == d:
            continue
        out.append(h)
    # Deterministic shuffle by route
    k = abs(hash(o + d))
    for i in range(len(out)):
        j = (i + k) % len(out)
        out[i], out[j] = out[j], out[i]
    return out[:5]


def base_duration_minutes(o: str, d: str, stops: int, hubs: str) -> int:
    return 320 + abs(hash(o + d + hubs + str(stops))) % 720


def row_1stop(
    o: str,
    d: str,
    hub: str,
    rank: int,
    hub_slot: int,
    dur_jitter: int,
    seq: int,
) -> list[str]:
    pat = HUB_1STOP.get(hub, HUB_1STOP["DXB"])
    d1, a1, d2, a2, off = pat
    # Jitter entire itinerary by 15–60 min steps for same-hub variety
    shift = dur_jitter * 20  # 0, 20, 40 → within typical 15–60 spacing
    d1, a1, d2, a2 = shift_clock(d1, shift), shift_clock(a1, shift), shift_clock(d2, shift), shift_clock(a2, shift)
    dur = base_duration_minutes(o, d, 1, hub) + dur_jitter * 18 + hub_slot * 7
    fn_a, fn_b = f"GA{9000 + seq}", f"GA{9001 + seq}"
    return [
        o,
        d,
        str(dur),
        "1",
        hub,
        str(rank),
        str(off),
        f"{d1}|{d2}",
        f"{a1}|{a2}",
        f"{fn_a}|{fn_b}",
    ]


def row_2stop(o: str, d: str, h1: str, h2: str, pattern_idx: int, rank: int, seq: int) -> list[str]:
    # Staggered times; different pattern_idx → different overall duration
    j = pattern_idx * 35
    d1 = fmt_t(12 * 60 + 25 + j)
    a1 = fmt_t(23 * 60 + 10 + j)
    d2 = fmt_t(1 * 60 + 20 + j)
    a2 = fmt_t(17 * 60 + 5 + j)
    d3 = fmt_t(19 * 60 + 20 + j)
    a3 = fmt_t(11 * 60 + 15 + j)
    dur = base_duration_minutes(o, d, 2, h1 + h2) + pattern_idx * 45 + 120
    off = 1
    fn1, fn2, fn3 = f"GA{9100 + seq}", f"GA{9101 + seq}", f"GA{9102 + seq}"
    return [
        o,
        d,
        str(dur),
        "2",
        f"{h1}|{h2}",
        str(rank),
        str(off),
        f"{d1}|{d2}|{d3}",
        f"{a1}|{a2}|{a3}",
        f"{fn1}|{fn2}|{fn3}",
    ]


def row_0stop_eu(o: str, d: str, rank: int, seq: int) -> list[str]:
    dur = 55 + abs(hash(o + d)) % 95
    dep = fmt_t(8 * 60 + 40 + (abs(hash(o)) % 5) * 15)
    arr = fmt_t(parse_t(dep) + dur)
    fn = f"GA{9200 + seq}"
    return [o, d, str(dur), "0", "", str(rank), "0", dep, arr, fn]


def row_direct_longhaul(o: str, d: str, rank: int, seq: int) -> list[str]:
    """Single non-stop leg for international / long routes (demo schedule, not exhaustive real ops)."""
    dur = 420 + abs(hash(o + d)) % 420  # ~7–14h gate-to-gate
    dep_m = (10 * 60 + (abs(hash(o + d)) % 14) * 25) % (24 * 60)
    total_arr = dep_m + dur
    off = total_arr // (24 * 60)
    arr_m = snap5(total_arr % (24 * 60))
    dep = fmt_t(dep_m)
    arr = fmt_t(arr_m)
    fn = f"GA{9300 + seq}"
    return [o, d, str(dur), "0", "", str(max(5, rank)), str(off), dep, arr, fn]


def write_sqlite(rows: list[list[str]]) -> None:
    """Create/replace flight_schedule_templates table (same column names as CSV header)."""
    DB_OUT.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(str(DB_OUT))
    try:
        cur = conn.cursor()
        cur.execute("DROP TABLE IF EXISTS flight_schedule_templates")
        cur.execute(
            """
            CREATE TABLE flight_schedule_templates (
                originCode TEXT,
                destCode TEXT,
                durationMinutes TEXT,
                stops TEXT,
                stopoverCodes TEXT,
                recommendedRankBase TEXT,
                arrivalOffsetDays TEXT,
                legDepartureTimes TEXT,
                legArrivalTimes TEXT,
                legFlightNumbers TEXT
            )
            """
        )
        cur.executemany(
            "INSERT INTO flight_schedule_templates VALUES (?,?,?,?,?,?,?,?,?,?)",
            rows,
        )
        conn.commit()
    finally:
        conn.close()


def write_csv(rows: list[list[str]], header: list[str]) -> None:
    CSV_OUT.parent.mkdir(parents=True, exist_ok=True)
    with CSV_OUT.open("w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(header)
        w.writerows(rows)


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate flight schedule templates into SQLite.")
    parser.add_argument(
        "--csv",
        action="store_true",
        help="Also write data/flight_schedule_templates.csv (optional; default is DB only).",
    )
    args = parser.parse_args()

    header = [
        "originCode",
        "destCode",
        "durationMinutes",
        "stops",
        "stopoverCodes",
        "recommendedRankBase",
        "arrivalOffsetDays",
        "legDepartureTimes",
        "legArrivalTimes",
        "legFlightNumbers",
    ]
    rows: list[list[str]] = []
    seq = 0
    for o in CODES:
        for d in CODES:
            if o == d:
                continue
            rank = 10 + abs(hash(o + d)) % 15
            hubs = pick_1stop_hubs(o, d)

            # Non-stop option for every long-haul / non–EU–EU pair (restores visible directs in results)
            if not (o in EU_UK and d in EU_UK):
                rows.append(row_direct_longhaul(o, d, rank - 2, seq))
                seq += 1

            # Short EU–EU direct + one connection
            if o in EU_UK and d in EU_UK:
                rows.append(row_0stop_eu(o, d, rank, seq))
                seq += 1
                h = hubs[0] if hubs[0] in {"AMS", "CDG", "FRA", "IST"} else "AMS"
                if h != o and h != d:
                    rows.append(row_1stop(o, d, h, rank + 1, 0, 0, seq))
                    seq += 1
                    rows.append(row_1stop(o, d, h, rank + 2, 1, 1, seq))
                    seq += 1

            # At least two 2-stop options (different hub pairs)
            pidx = abs(hash(o + d)) % len(TWO_STOP_PATTERNS)
            h1, h2 = TWO_STOP_PATTERNS[pidx]
            if h1 not in (o, d) and h2 not in (o, d) and h1 != h2:
                rows.append(row_2stop(o, d, h1, h2, 0, rank, seq))
                seq += 1
            h1b, h2b = TWO_STOP_PATTERNS[(pidx + 3) % len(TWO_STOP_PATTERNS)]
            if h1b not in (o, d) and h2b not in (o, d) and h1b != h2b and (h1b, h2b) != (h1, h2):
                rows.append(row_2stop(o, d, h1b, h2b, 1, rank + 1, seq))
                seq += 1

            # One-stop: same hub, duration steps (15–60 min apart via jitter)
            for hi, hub in enumerate(hubs[:3]):
                if hub == o or hub == d:
                    continue
                rows.append(row_1stop(o, d, hub, rank + hi, hi, 0, seq))
                seq += 1
                rows.append(row_1stop(o, d, hub, rank + hi + 3, hi, 1, seq))
                seq += 1
                rows.append(row_1stop(o, d, hub, rank + hi + 6, hi, 2, seq))
                seq += 1

    write_sqlite(rows)
    print(f"Wrote {len(rows)} rows to {DB_OUT}")
    if args.csv:
        write_csv(rows, header)
        print(f"Also wrote {len(rows)} rows to {CSV_OUT}")


if __name__ == "__main__":
    main()
