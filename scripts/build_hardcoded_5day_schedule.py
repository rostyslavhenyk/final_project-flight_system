#!/usr/bin/env python3
"""Build a hardcoded 5-day repeating schedule table.

This script materializes `data/db/flight_schedule_templates.db` into a fixed
5-day cycle with:
- GA#### flight numbers
- connecting onward legs aligned to real direct leg services
- minimum 5 itineraries per route per cycle day
"""

from __future__ import annotations

import sqlite3
from dataclasses import dataclass
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DB_PATH = ROOT / "data" / "db" / "flight_schedule_templates.db"
CYCLE_DAYS = 5
PER_DAY_PER_ROUTE = 5


def ga4(index: int) -> str:
    return f"GA{1000 + (index % 9000):04d}"


def minute_of_day(hhmm: str) -> int:
    h, m = hhmm.split(":")
    return int(h) * 60 + int(m)


def compute_offsets_and_duration(dep_times: list[str], arr_times: list[str]) -> tuple[list[int], int]:
    n = min(len(dep_times), len(arr_times))
    if n == 0:
        return [], 1
    arr_offsets: list[int] = []
    dep_day = 0
    for i in range(n):
        dep_min = minute_of_day(dep_times[i])
        arr_min = minute_of_day(arr_times[i])
        arr_day = dep_day + (1 if arr_min < dep_min else 0)
        arr_offsets.append(arr_day)
        if i < n - 1:
            next_dep_min = minute_of_day(dep_times[i + 1])
            dep_day = arr_day + (1 if next_dep_min < arr_min else 0)

    first_dep_abs = minute_of_day(dep_times[0])
    last_arr_abs = arr_offsets[-1] * 24 * 60 + minute_of_day(arr_times[-1])
    duration = max(1, last_arr_abs - first_dep_abs)
    return arr_offsets, duration


@dataclass
class TemplateRow:
    origin: str
    dest: str
    duration: str
    stops: int
    stopovers: list[str]
    rank: int
    arrival_offset: str
    dep_times: list[str]
    arr_times: list[str]
    fns: list[str]

    def key(self) -> tuple[str, str, str, str, str]:
        return (
            self.origin,
            self.dest,
            "|".join(self.stopovers),
            "|".join(self.dep_times),
            "|".join(self.arr_times),
        )

    def path(self) -> list[str]:
        return [self.origin, *self.stopovers, self.dest]

    def clone(self) -> "TemplateRow":
        return TemplateRow(
            origin=self.origin,
            dest=self.dest,
            duration=self.duration,
            stops=self.stops,
            stopovers=list(self.stopovers),
            rank=self.rank,
            arrival_offset=self.arrival_offset,
            dep_times=list(self.dep_times),
            arr_times=list(self.arr_times),
            fns=list(self.fns),
        )


def load_rows(conn: sqlite3.Connection) -> list[TemplateRow]:
    cur = conn.cursor()
    raw = cur.execute(
        """
        SELECT originCode, destCode, durationMinutes, stops, stopoverCodes,
               recommendedRankBase, arrivalOffsetDays, legDepartureTimes,
               legArrivalTimes, legFlightNumbers
        FROM flight_schedule_templates
        """
    ).fetchall()
    out: list[TemplateRow] = []
    for o, d, dur, stops, sc, rank, off, deps, arrs, fns in raw:
        s = int((stops or "0").strip() or "0")
        stopovers = [x.strip().upper() for x in (sc or "").split("|") if x.strip()]
        dep_times = [x.strip() for x in (deps or "").split("|") if x.strip()]
        arr_times = [x.strip() for x in (arrs or "").split("|") if x.strip()]
        fn_list = [x.strip().upper() for x in (fns or "").split("|") if x.strip()]
        expected = s + 1
        if len(dep_times) != expected or len(arr_times) != expected:
            continue
        if len(fn_list) != expected:
            fn_list = [""] * expected
        out.append(
            TemplateRow(
                origin=(o or "").strip().upper(),
                dest=(d or "").strip().upper(),
                duration=(dur or "").strip(),
                stops=s,
                stopovers=stopovers,
                rank=int((rank or "999").strip() or "999"),
                arrival_offset=(off or "").strip(),
                dep_times=dep_times,
                arr_times=arr_times,
                fns=fn_list,
            )
        )
    return out


def assign_ga4_and_align_legs(rows: list[TemplateRow]) -> list[TemplateRow]:
    sig_to_fno: dict[tuple[str, str, str, str], str] = {}
    nonstop_by_route: dict[tuple[str, str], list[tuple[str, str, str]]] = {}
    next_id = 0

    # Canonical direct services.
    for row in rows:
        if row.stops != 0:
            continue
        sig = (row.origin, row.dest, row.dep_times[0], row.arr_times[0])
        fno = sig_to_fno.get(sig)
        if fno is None:
            fno = ga4(next_id)
            sig_to_fno[sig] = fno
            next_id += 1
        row.fns[0] = fno
        nonstop_by_route.setdefault((row.origin, row.dest), []).append((row.dep_times[0], row.arr_times[0], fno))

    for options in nonstop_by_route.values():
        options.sort(key=lambda x: (x[0], x[1], x[2]))

    for row in rows:
        path = row.path()
        # first map all existing leg signatures to stable GA####.
        for i in range(row.stops + 1):
            sig = (path[i], path[i + 1], row.dep_times[i], row.arr_times[i])
            fno = sig_to_fno.get(sig)
            if fno is None:
                fno = ga4(next_id)
                sig_to_fno[sig] = fno
                next_id += 1
            row.fns[i] = fno

        # then align onward legs to searchable direct services.
        for i in range(1, row.stops + 1):
            leg_origin, leg_dest = path[i], path[i + 1]
            options = nonstop_by_route.get((leg_origin, leg_dest))
            if not options:
                continue
            target_dep = row.dep_times[i]
            dep, arr, fno = min(options, key=lambda x: abs(minute_of_day(x[0]) - minute_of_day(target_dep)))
            row.dep_times[i] = dep
            row.arr_times[i] = arr
            row.fns[i] = fno

    return rows


def build_5day_rows(rows: list[TemplateRow]) -> list[tuple]:
    by_route: dict[tuple[str, str], list[TemplateRow]] = {}
    for row in rows:
        by_route.setdefault((row.origin, row.dest), []).append(row)

    out: list[tuple] = []
    for (_, _), route_rows in by_route.items():
        unique = list({r.key(): r for r in route_rows}.values())
        ordered = sorted(unique, key=lambda r: (r.rank, r.stops, r.dep_times[0], r.arr_times[-1]))
        if not ordered:
            continue

        for day in range(1, CYCLE_DAYS + 1):
            # Keep all direct services each day so stop-leg searches always find
            # the corresponding standalone route service.
            directs = [r for r in ordered if r.stops == 0]
            nondirects = [r for r in ordered if r.stops > 0]
            day_rows: list[TemplateRow] = [r.clone() for r in directs]

            base = (day - 1) * PER_DAY_PER_ROUTE
            needed_nondirect = max(PER_DAY_PER_ROUTE, PER_DAY_PER_ROUTE - len(day_rows))
            if nondirects:
                for j in range(needed_nondirect):
                    day_rows.append(nondirects[(base + j) % len(nondirects)].clone())

            # Ensure at least PER_DAY_PER_ROUTE rows even on routes with only directs.
            while len(day_rows) < PER_DAY_PER_ROUTE:
                day_rows.append(ordered[len(day_rows) % len(ordered)].clone())

            for pick in day_rows:
                # Slight day-based rank shift keeps deterministic ordering per day.
                rank = pick.rank + (day - 1)
                arr_offsets, computed_duration = compute_offsets_and_duration(pick.dep_times, pick.arr_times)
                computed_arrival_offset = arr_offsets[-1] if arr_offsets else 0
                out.append(
                    (
                        pick.origin,
                        pick.dest,
                        str(computed_duration),
                        str(pick.stops),
                        "|".join(pick.stopovers),
                        str(rank),
                        str(computed_arrival_offset),
                        "|".join(pick.dep_times),
                        "|".join(pick.arr_times),
                        "|".join(pick.fns),
                        str(day),
                    )
                )
    return out


def rewrite_table(conn: sqlite3.Connection, rows_5day: list[tuple]) -> None:
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
            legFlightNumbers TEXT,
            cycleDay TEXT
        )
        """
    )
    cur.executemany(
        "INSERT INTO flight_schedule_templates VALUES (?,?,?,?,?,?,?,?,?,?,?)",
        rows_5day,
    )
    conn.commit()


def main() -> None:
    if not DB_PATH.exists():
        raise SystemExit(f"Missing DB: {DB_PATH}")
    conn = sqlite3.connect(str(DB_PATH))
    try:
        source_rows = load_rows(conn)
        aligned = assign_ga4_and_align_legs(source_rows)
        rows_5day = build_5day_rows(aligned)
        rewrite_table(conn, rows_5day)
    finally:
        conn.close()
    print(f"Source rows loaded: {len(source_rows)}")
    print(f"Hardcoded 5-day rows written: {len(rows_5day)}")


if __name__ == "__main__":
    main()
