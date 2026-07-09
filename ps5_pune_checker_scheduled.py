#!/usr/bin/env python3
"""
PS5 Availability Checker - Pune (scheduled + email alerts)
===========================================================
Runs one full sweep of Pune pincodes, emails you if any PS5 is in stock,
then exits — perfect for Windows Task Scheduler or cron.

SETUP (one time):
  1. pip install requests
  2. Fill in the EMAIL CONFIG section below.
     For Gmail: create an "App Password" at
     https://myaccount.google.com/apppasswords  (requires 2FA enabled)
     and paste the 16-character password into SMTP_PASSWORD.
  3. Test it once manually:  python ps5_pune_checker_scheduled.py
  4. Schedule it (see instructions provided alongside this script).

Behavior:
  - Appends every run to ps5_checker.log (so you can verify it's running)
  - Writes full results to ps5_availability_pune.csv each run
  - Sends email ONLY when stock is found (no spam on empty runs)
  - Remembers what it already alerted about (alert_state.txt) so you
    don't get the same email every 30 minutes while stock stays live
"""

import csv
import os
import smtplib
import sys
import time
from datetime import datetime
from email.mime.text import MIMEText

import requests

# ===========================================================================
# EMAIL CONFIG  <<< EDIT THIS SECTION >>>
# ===========================================================================
SMTP_HOST = "smtp.gmail.com"          # Gmail. For Outlook: smtp.office365.com
SMTP_PORT = 587
SMTP_USER = "your.email@gmail.com"    # the account that SENDS the alert
SMTP_PASSWORD = "xxxx xxxx xxxx xxxx" # Gmail App Password (NOT your login password)
ALERT_TO = "your.email@gmail.com"     # where the alert should arrive
# ===========================================================================

# --- Paths (everything lives next to the script, regardless of cwd) --------
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
LOG_FILE = os.path.join(BASE_DIR, "ps5_checker.log")
CSV_FILE = os.path.join(BASE_DIR, "ps5_availability_pune.csv")
STATE_FILE = os.path.join(BASE_DIR, "alert_state.txt")

# --- Pune pincodes ----------------------------------------------------------
PUNE_PINCODES = [str(p) for p in range(411001, 411063)] + [
    "410501", "410506", "410507", "412105", "412107", "412114",
    "412201", "412207", "412208", "412307", "412308",
]

# --- Products (codes from product-page URLs; update if retailers change) ----
CROMA_PRODUCTS = {
    "PS5 Slim Disc": "305919",
    "PS5 Slim Digital": "305920",
    "PS5 Pro": "312292",
}
RELIANCE_PRODUCTS = {
    "PS5 Slim Disc": "493177787",
    "PS5 Slim Digital": "493177788",
}

HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
        "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
    ),
    "Accept": "application/json",
}
DELAY = 0.6          # seconds between requests
MAX_RETRIES = 2      # retry transient errors


def log(msg: str):
    line = f"[{datetime.now():%Y-%m-%d %H:%M:%S}] {msg}"
    print(line)
    with open(LOG_FILE, "a", encoding="utf-8") as f:
        f.write(line + "\n")


# --- Retailer checks (with simple retry) ------------------------------------
def _get_json(url, params):
    last_err = None
    for attempt in range(MAX_RETRIES + 1):
        try:
            r = requests.get(url, params=params, headers=HEADERS, timeout=15)
            if r.status_code == 200:
                return r.json(), None
            last_err = f"HTTP {r.status_code}"
        except requests.RequestException as e:
            last_err = e.__class__.__name__
        except ValueError:
            last_err = "bad JSON"
        time.sleep(1.5 * (attempt + 1))
    return None, last_err


def check_croma(product_code, pincode):
    data, err = _get_json(
        "https://api.croma.com/inventory/oms/v2/tms/detail",
        {"promise": "Y", "mode": "H", "pincode": pincode,
         "productCode": product_code, "quantity": 1},
    )
    if err:
        return f"ERROR: {err}"
    text = str(data).lower()
    if "not serviceable" in text or "out of stock" in text:
        return "OUT OF STOCK"
    if data.get("pinCodeDetails") or data.get("promiseDetails") or "eddmessage" in text:
        return "IN STOCK"
    return "OUT OF STOCK"


def check_reliance(article_id, pincode):
    data, err = _get_json(
        "https://www.reliancedigital.in/rildigitalws/v2/rrldigital/cms/pincode/serviceability",
        {"pincode": pincode, "articleCode": article_id},
    )
    if err:
        return f"ERROR: {err}"
    text = str(data).lower()
    if "'serviceable': true" in text or '"serviceable": true' in text or '"isserviceable": true' in text:
        return "IN STOCK"
    return "OUT OF STOCK"


# --- Alert de-duplication ----------------------------------------------------
def load_alerted():
    if not os.path.exists(STATE_FILE):
        return set()
    with open(STATE_FILE, encoding="utf-8") as f:
        return {line.strip() for line in f if line.strip()}


def save_alerted(keys: set):
    with open(STATE_FILE, "w", encoding="utf-8") as f:
        f.write("\n".join(sorted(keys)))


# --- Email -------------------------------------------------------------------
def send_email(hits):
    body_lines = ["PS5 IN STOCK! Grab it fast:\n"]
    links = {
        "Croma": "https://www.croma.com/searchB?q=playstation%205",
        "Reliance Digital": "https://www.reliancedigital.in/search?q=playstation%205",
    }
    for retailer, product, pin in hits:
        body_lines.append(f"  • {retailer} — {product} — deliverable to {pin}")
    body_lines.append("\nBuy links:")
    for name, url in links.items():
        body_lines.append(f"  {name}: {url}")
    body_lines.append(f"\nChecked at {datetime.now():%Y-%m-%d %H:%M:%S}")

    msg = MIMEText("\n".join(body_lines))
    msg["Subject"] = f"🎮 PS5 IN STOCK — {len(hits)} hit(s) in Pune!"
    msg["From"] = SMTP_USER
    msg["To"] = ALERT_TO

    with smtplib.SMTP(SMTP_HOST, SMTP_PORT, timeout=30) as server:
        server.starttls()
        server.login(SMTP_USER, SMTP_PASSWORD)
        server.send_message(msg)


# --- Main --------------------------------------------------------------------
def main():
    pincodes = sys.argv[1:] if len(sys.argv) > 1 else PUNE_PINCODES
    log(f"Run started — {len(pincodes)} pincodes")

    results, hits, errors = [], [], 0
    for pin in pincodes:
        for name, code in CROMA_PRODUCTS.items():
            status = check_croma(code, pin)
            results.append(("Croma", name, pin, status))
            if status == "IN STOCK":
                hits.append(("Croma", name, pin))
            elif status.startswith("ERROR"):
                errors += 1
            time.sleep(DELAY)
        for name, code in RELIANCE_PRODUCTS.items():
            status = check_reliance(code, pin)
            results.append(("Reliance Digital", name, pin, status))
            if status == "IN STOCK":
                hits.append(("Reliance Digital", name, pin))
            elif status.startswith("ERROR"):
                errors += 1
            time.sleep(DELAY)

    with open(CSV_FILE, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["retailer", "product", "pincode", "status"])
        w.writerows(results)

    # Only email about hits we haven't already alerted on
    alerted = load_alerted()
    current_keys = {f"{r}|{p}|{pin}" for r, p, pin in hits}
    new_hits = [h for h in hits if f"{h[0]}|{h[1]}|{h[2]}" not in alerted]

    if new_hits:
        try:
            send_email(new_hits)
            log(f"ALERT SENT — {len(new_hits)} new hit(s): {new_hits}")
        except Exception as e:
            log(f"EMAIL FAILED ({e.__class__.__name__}: {e}) — hits were: {new_hits}")
    elif hits:
        log(f"{len(hits)} hit(s) still live, already alerted — no email")
    else:
        log(f"No stock. ({errors} request errors)" if errors else "No stock.")

    # Reset state when stock disappears so the next restock re-alerts
    save_alerted(current_keys)
    log("Run finished")


if __name__ == "__main__":
    main()
