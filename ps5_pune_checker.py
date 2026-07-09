#!/usr/bin/env python3
"""
PS5 Availability Checker - Pune (all pincodes)
================================================
Checks PlayStation 5 stock/deliverability across every Pune-region pincode
on retailers that expose pincode-level serviceability APIs:

  1. Croma           - official inventory/promise API (most reliable)
  2. Reliance Digital - pincode serviceability API

Amazon & Flipkart don't expose stable public pincode APIs (they aggressively
block bots), so this script focuses on the two above. Results are printed
to the console and saved to ps5_availability_pune.csv.

Usage:
    pip install requests
    python ps5_pune_checker.py                 # check all Pune pincodes
    python ps5_pune_checker.py 411001 411045   # check specific pincodes only

NOTE: Retailer APIs change without notice. If a retailer starts returning
errors, open the product page in a browser with DevTools > Network open,
find the pincode-check request, and update the URL/params below.
"""

import csv
import sys
import time
import requests

# ---------------------------------------------------------------------------
# 1. Pune pincodes
#    411001-411062 = Pune city + PCMC core
#    Plus common surrounding areas (Hinjewadi, Wagholi, Talegaon, Chakan...)
# ---------------------------------------------------------------------------
PUNE_PINCODES = [str(p) for p in range(411001, 411063)] + [
    "410501",  # Chakan
    "410506",  # Rajgurunagar
    "410507",  # Talegaon Dabhade
    "412105",  # Alandi
    "412107",  # Hinjewadi Phase 3 area
    "412114",  # Dehu
    "412201",  # Loni Kalbhor
    "412207",  # Wagholi
    "412208",  # Manjari
    "412307",  # Hadapsar/Phursungi outskirts
    "412308",  # Uruli Kanchan side
]

# ---------------------------------------------------------------------------
# 2. Products to track (add/remove SKUs as needed)
#    Croma product codes come from the product page URL, e.g.
#    croma.com/...-/p/305919  ->  productCode = "305919"
# ---------------------------------------------------------------------------
CROMA_PRODUCTS = {
    "PS5 Slim Disc": "305919",
    "PS5 Slim Digital": "305920",
    "PS5 Pro": "312292",
}

# Reliance Digital article/SKU codes (from product page URL, e.g.
# reliancedigital.in/sony-ps5-.../p/493177787 -> "493177787")
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

DELAY_BETWEEN_REQUESTS = 0.6  # seconds - be polite, avoid getting rate-limited


# ---------------------------------------------------------------------------
# Retailer checkers
# ---------------------------------------------------------------------------
def check_croma(product_code: str, pincode: str) -> str:
    """Returns 'IN STOCK', 'OUT OF STOCK', or 'ERROR: ...'"""
    url = "https://api.croma.com/inventory/oms/v2/tms/detail"
    params = {
        "promise": "Y",
        "mode": "H",
        "pincode": pincode,
        "productCode": product_code,
        "quantity": 1,
    }
    try:
        r = requests.get(url, params=params, headers=HEADERS, timeout=15)
        if r.status_code != 200:
            return f"ERROR: HTTP {r.status_code}"
        data = r.json()
        # Croma returns promise/delivery info when deliverable
        text = str(data).lower()
        if "not serviceable" in text or "out of stock" in text:
            return "OUT OF STOCK"
        if data.get("pinCodeDetails") or data.get("promiseDetails") or "eddmessage" in text:
            return "IN STOCK"
        return "OUT OF STOCK"
    except requests.RequestException as e:
        return f"ERROR: {e.__class__.__name__}"
    except ValueError:
        return "ERROR: bad JSON"


def check_reliance(article_id: str, pincode: str) -> str:
    """Returns 'IN STOCK', 'OUT OF STOCK', or 'ERROR: ...'"""
    url = "https://www.reliancedigital.in/rildigitalws/v2/rrldigital/cms/pincode/serviceability"
    params = {"pincode": pincode, "articleCode": article_id}
    try:
        r = requests.get(url, params=params, headers=HEADERS, timeout=15)
        if r.status_code != 200:
            return f"ERROR: HTTP {r.status_code}"
        data = r.json()
        text = str(data).lower()
        if '"serviceable": true' in text or "'serviceable': true" in text or '"isserviceable": true' in text:
            return "IN STOCK"
        return "OUT OF STOCK"
    except requests.RequestException as e:
        return f"ERROR: {e.__class__.__name__}"
    except ValueError:
        return "ERROR: bad JSON"


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------
def main():
    pincodes = sys.argv[1:] if len(sys.argv) > 1 else PUNE_PINCODES
    results = []
    hits = []

    total_checks = len(pincodes) * (len(CROMA_PRODUCTS) + len(RELIANCE_PRODUCTS))
    print(f"Checking {len(pincodes)} Pune pincodes x "
          f"{len(CROMA_PRODUCTS) + len(RELIANCE_PRODUCTS)} products "
          f"({total_checks} requests, ~{int(total_checks * DELAY_BETWEEN_REQUESTS / 60)} min)\n")

    done = 0
    for pin in pincodes:
        for name, code in CROMA_PRODUCTS.items():
            status = check_croma(code, pin)
            results.append(("Croma", name, pin, status))
            if status == "IN STOCK":
                hits.append(("Croma", name, pin))
                print(f"  ✅ {pin}  Croma            {name}: IN STOCK")
            done += 1
            time.sleep(DELAY_BETWEEN_REQUESTS)

        for name, code in RELIANCE_PRODUCTS.items():
            status = check_reliance(code, pin)
            results.append(("Reliance Digital", name, pin, status))
            if status == "IN STOCK":
                hits.append(("Reliance Digital", name, pin))
                print(f"  ✅ {pin}  Reliance Digital {name}: IN STOCK")
            done += 1
            time.sleep(DELAY_BETWEEN_REQUESTS)

        print(f"  ... {pin} done ({done}/{total_checks})", end="\r")

    # Save full results
    out_file = "ps5_availability_pune.csv"
    with open(out_file, "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["retailer", "product", "pincode", "status"])
        w.writerows(results)

    print("\n" + "=" * 60)
    if hits:
        print(f"🎉 PS5 AVAILABLE at {len(hits)} retailer/pincode combos:")
        for retailer, name, pin in hits:
            print(f"   {retailer:18s} {name:20s} pincode {pin}")
    else:
        print("😞 No stock found at any Pune pincode right now.")
    print(f"Full results saved to {out_file}")
    print("Tip: run this on a cron/scheduled task every 30 min to catch restocks.")


if __name__ == "__main__":
    main()
