# Price feed format (PRD F-09.2)

The app reads one static national file of city fuel prices. **Status (19 Oct 2026): no source and no host yet** (PRD §13 D2, D3), so the client ships switched off:

- `FeatureFlags.price_feed` is `false`, and
- `PriceFeedEndpoint.CONFIGURED.url` is `null`. There is no feed URL anywhere in the code or config.

The worker does nothing unless **both** are set. Choosing a URL needs the owner's approval and an entry in PRD §9 (new network endpoint, CLAUDE.md rule 7). Until then users type prices themselves (F-09.1).

## Fetching

- **One plain `GET`** of the whole file. No query string, cookies, account, device id or location (CLAUDE.md rule 5). The app filters the file on the device, keeping only the home city and up to 3 pinned cities.
- **At most once a day, after 06:30 IST** (`PriceFeedSchedule`). The oil companies revise prices at 06:00 IST. India time is used whatever zone the phone is in.
- WorkManager wakes the job about every 8 hours, with a random start up to 90 minutes after 06:30 so clients don't all arrive at once. It needs a network connection. A download failure retries with backoff (up to 3 times); a malformed file waits for the next day.
- **Compression:** serve the file with `Content-Encoding: gzip` (OkHttp unwraps it), or as a raw `.json.gz`. The client spots the gzip magic bytes and unwraps it.
- **Size:** files over 16 MB are refused.

## Format, schema 1

UTF-8 JSON. Money is **integer paise** per unit (₹/L; ₹/kg for CNG), never a decimal.

```json
{
  "schema": 1,
  "generatedAt": "2026-10-19T06:05:00+05:30",
  "source": "<name of the licensed source>",
  "licence": "<licence or terms URL>",
  "cities": [
    {
      "id": "bengaluru",
      "name": "Bengaluru",
      "state": "Karnataka",
      "prices": {
        "petrol":         {"start": "2026-09-20", "paise": [10292, 10292, null, 10292]},
        "diesel":         {"start": "2026-09-20", "paise": [8895, 8895, 8895, 8895]},
        "cng":            {"start": "2026-09-20", "paise": [8900, 8900, 8900, 8900]},
        "premium_petrol": {"start": "2026-09-20", "paise": [11150, 11150, 11150, 11150]}
      }
    }
  ]
}
```

(Illustrative numbers, not live data.)

| Field | Rule |
|---|---|
| `schema` | Required integer. The app rejects a file with a schema newer than it supports (1). Additive changes keep `1`. |
| `generatedAt` | ISO-8601 with offset. Optional; informational. |
| `cities[].id` | Required. The permanent ids from `core/data/src/main/assets/prices/cities.json`. Cities with unknown ids are ignored until the app's list knows them. |
| `cities[].prices` | Keyed by fuel: `petrol`, `diesel`, `cng`, `premium_petrol`. Unknown fuels are ignored. |
| `start` + `paise` | Value `i` is the price on `start + i` days (IST calendar days): today plus the previous 30 days, so 31 values. `null` means no price that day. |

**Tolerance:** unknown fields are ignored. A city without an id, a series without a valid `start`, a non-number value, or a price outside ₹10–₹1,000 is skipped, not fatal. Only a file that isn't JSON, or has no `schema` or `cities`, is rejected.

## What the app does with it

- Rows are written to the same `fuel_price` table as typed prices, with origin `Feed`. One row per city, fuel and day. **A price the user typed for a day is never overwritten.** Unchanged prices aren't rewritten.
- **Price alerts (F-09.5):** opt-in. After a feed run, the home city's prices in effect before and after are compared. A move of ₹1 or more per L or kg posts one notification per fuel on the "Price alerts" channel. Typed prices never alert.
- The bundled city list can later be refreshed from the same feed. Not implemented: R1 ships the list in the APK (`cities.json`, `version` 1).
