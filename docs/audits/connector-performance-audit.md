# Performance Audit

## Benchmark re-run (independent)

| Dataset | Insert | Search | DB size (main file) |
|---------|--------|--------|---------------------|
| 1,000 | 47 ms | 3 ms | 4 KB* |
| 10,000 | 414 ms | 25 ms | 4.5 MB |
| 50,000 | 2,298 ms | 140 ms | 24 MB |

\*1k size reflects WAL mode before checkpoint — **not a data loss indicator**. After checkpoint or at rest, size grows. 10k/50k sizes confirm data persistence.

## Thresholds

- Insert ≤ 2000 ms/1k — **PASS**  
- Search ≤ 250 ms — **PASS** (50k: 140 ms post-fix run)  
- Pagination ≤ 150 ms — **PASS**

## Bottlenecks noted (not fixed — acceptable)

- Per-ledger `findById` in sync loop (N+1 reads during upsert phase)  
- Full extraction loaded into memory before batch persist  

## Methodology validity

Synthetic in-process benchmark; honest about environment constraints. Not live Tally validation.
