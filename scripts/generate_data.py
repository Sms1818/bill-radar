"""Synthetic billing data for Bill Radar. ALL DATA IS FAKE.

Column names are approximations of AWS CUR and Azure Cost Management exports,
written from memory. Check them against the providers' docs before claiming
real-format support.

Scenario: today is 2026-10-12, month-end is 2026-10-31.
Planted situations (so the engine has something to find):
  analytics      runaway Athena queries, cost climbing daily  -> will breach
  ml-training    new GPU cluster on day 9, cost steps up      -> will breach
  internal-tools close to its budget                          -> warning
  checkout       stable, well under budget                    -> ok
  search         split across AWS and Azure, under budget     -> ok
Planted data problems: untagged rows, EUR rows mixed with USD, a negative
credit, one duplicated row.
"""
import csv, json, os, random, sys
from datetime import date, timedelta

random.seed(42)
OUT = sys.argv[1] if len(sys.argv) > 1 else "bill-radar-data"
os.makedirs(OUT, exist_ok=True)

START, TODAY = date(2026, 10, 1), date(2026, 10, 12)
EUR_TO_USD = 1.12  # configured synthetic fixture rate: 1 EUR = 1.12 USD
EUR_PER_USD = 1 / EUR_TO_USD  # USD -> EUR
days = [START + timedelta(d) for d in range((TODAY - START).days + 1)]
noise = lambda: random.uniform(0.94, 1.06)

# (provider, workload, service, region, cost_fn(day_index 1..12))
streams = [
    ("aws", "checkout", "AmazonEC2", "eu-central-1", lambda d: 130),
    ("aws", "checkout", "AmazonRDS", "eu-central-1", lambda d: 75),
    ("aws", "checkout", "AmazonCloudFront", "eu-central-1", lambda d: 25),
    ("aws", "search", "AmazonES", "eu-west-1", lambda d: 70),
    ("azure", "search", "Virtual Machines", "westeurope", lambda d: 60),
    ("aws", "analytics", "AmazonEC2", "us-east-1", lambda d: 150),
    ("aws", "analytics", "AmazonAthena", "us-east-1",
     lambda d: 40 if d <= 5 else 40 * 1.12 ** (d - 5)),
    ("azure", "ml-training", "Virtual Machines", "northeurope",
     lambda d: 300 if d <= 8 else 520),
    ("azure", "ml-training", "Storage", "northeurope", lambda d: 40),
    ("aws", "internal-tools", "AmazonEC2", "eu-central-1", lambda d: 55),
    ("aws", "internal-tools", "AmazonS3", "eu-central-1", lambda d: 12),
]

aws_rows, azure_rows = [], []
for provider, workload, service, region, fn in streams:
    for i, day in enumerate(days, start=1):
        usd = round(fn(i) * noise(), 2)
        if provider == "aws":
            tag = "" if random.random() < 0.06 else workload  # ~6% untagged
            aws_rows.append({
                "lineItem/UsageStartDate": day.isoformat() + "T00:00:00Z",
                "lineItem/ProductCode": service,
                "product/region": region,
                "lineItem/UnblendedCost": usd,
                "lineItem/CurrencyCode": "USD",
                "resourceTags/user:workload": tag,
            })
        else:
            eur = workload == "ml-training" and service == "Storage" and i in (3, 4, 5, 6, 7)
            cost, cur = (round(usd * EUR_PER_USD, 2), "EUR") if eur else (usd, "USD")
            tags = "" if random.random() < 0.04 else json.dumps({"workload": workload})
            azure_rows.append({
                "Date": day.isoformat(),
                "ServiceName": service,
                "ResourceLocation": region,
                "CostInBillingCurrency": cost,
                "BillingCurrency": cur,
                "Tags": tags,
            })

# a negative credit and one exact duplicate row
aws_rows.append({
    "lineItem/UsageStartDate": "2026-10-04T00:00:00Z", "lineItem/ProductCode": "AmazonEC2",
    "product/region": "us-east-1", "lineItem/UnblendedCost": -150.00,
    "lineItem/CurrencyCode": "USD", "resourceTags/user:workload": "analytics"})
azure_rows.append(dict(azure_rows[20]))


def write(name, rows):
    with open(os.path.join(OUT, name), "w", newline="") as f:
        w = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
        w.writeheader()
        w.writerows(rows)


write("aws_cur_style.csv", aws_rows)
write("azure_cost_style.csv", azure_rows)

budgets = {"checkout": 9000, "search": 6000, "analytics": 5000,
           "ml-training": 12000, "internal-tools": 2250}
with open(os.path.join(OUT, "budgets.json"), "w") as f:
    eur_budgets = {workload: round(amount / EUR_TO_USD, 2) for workload, amount in budgets.items()}
    json.dump({"month": "2026-10", "as_of": "2026-10-12", "currency": "EUR",
               "fx_to_usd": {"EUR": EUR_TO_USD}, "budgets": eur_budgets}, f, indent=2)

# Designed outcomes, for tests (not an answer key for exact numbers).
with open(os.path.join(OUT, "expected.json"), "w") as f:
    json.dump({"breach": ["analytics", "ml-training"], "warning": ["internal-tools"],
               "ok": ["checkout", "search"],
               "data_quality": {"untagged_aws_rows": "about 6%", "mixed_currency": "EUR",
                                "negative_rows": 1, "duplicate_rows": 1}}, f, indent=2)

# Self-check: naive forecast = spend so far + mean of last 7 days x remaining days.
def to_usd(v, cur): return v * (EUR_TO_USD if cur == "EUR" else 1)
daily = {}
for r in aws_rows:
    if r["resourceTags/user:workload"]:
        k = (r["resourceTags/user:workload"], r["lineItem/UsageStartDate"][:10])
        daily[k] = daily.get(k, 0) + float(r["lineItem/UnblendedCost"])
seen = set()
for r in azure_rows:
    key = tuple(r.values())
    if key in seen or not r["Tags"]:
        continue
    seen.add(key)
    w = json.loads(r["Tags"])["workload"]
    k = (w, r["Date"])
    daily[k] = daily.get(k, 0) + to_usd(float(r["CostInBillingCurrency"]), r["BillingCurrency"])
print(f"{len(aws_rows)} AWS rows, {len(azure_rows)} Azure rows -> {OUT}/")
for w, b in budgets.items():
    series = [daily.get((w, d.isoformat()), 0) for d in days]
    fc = sum(series) + (sum(series[-7:]) / 7) * 19
    print(f"  {w:15s} budget {b:6d}  naive forecast {fc:8.0f}  ({fc / b:5.0%})")
