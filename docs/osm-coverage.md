# OpenStreetMap station coverage (PRD D14)

The bundled station list (PRD F-21.2) is built from OpenStreetMap. Coverage in India is uneven, so before launch
check 30 pumps you know in each city and record whether the list has each one within 40 m (the fill-up detection
radius, PRD F-21.4) and within 150 m.

**Build of 23 Oct 2026:** 19,956 stations (19,348 fuel, 608 charging), 297 KB compressed. India has roughly
90,000 fuel stations, so expect gaps. A spot check found 6 stations within 2 km of central Mumbai (19.0760, 72.8777).

## How to check a pump

Stand at (or look up) the pump's coordinates, then:

```bash
./gradlew :tools:stations:run --args="near core/data/src/main/assets/stations/india.tsv.gz 12.97842 77.64081 150"
```

It prints every station in the bundled file within the radius, nearest first, with its OSM id.

## Results

Fill in one row per pump. "Hit" = the right station is in the list within 40 m.

| City | Pump (brand · area) | Lat, lng | Within 40 m | Within 150 m | Notes |
|---|---|---|---|---|---|
| Bengaluru |  |  |  |  |  |
| Delhi |  |  |  |  |  |
| Mumbai |  |  |  |  |  |

**Hit rate:** Bengaluru _/30 · Delhi _/30 · Mumbai _/30

If a city's hit rate is under 60% (PRD D14), consider seeding personal stations from the first two fill-ups and
lowering the F-21.1 / F-21.4 ACs.

## Refreshing the list

`fetch` asks the public Overpass API once and caches its answer in `tools/stations/build/` (git-ignored); `rebuild`
re-encodes from that cache without asking Overpass again. Refresh once per release at most.

```bash
./gradlew :tools:stations:run --args="fetch core/data/src/main/assets/stations/india.tsv.gz"
```
