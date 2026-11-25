# 3. Report on Code Coverage Test (>65% Branch Coverage)

[← Unit Testing Report](02_unit_testing_report.md) · [Submission Hub](README.md) · [Next: JavaDoc Documentation →](04_javadoc_documentation.md)

Code coverage is measured automatically via **JaCoCo** during `./mvnw clean test` across the **entire codebase** with **zero exclusions** configured in `pom.xml` (all 190 compiled classes—including models, services, utilities, database layer, and JavaFX UI controllers—are included in the global calculation).

---

## 3.1 Global JaCoCo Metrics

| Coverage Dimension | Covered / Total | Percentage | Course Requirement | Margin |
|---| :---: | :---: | :---: | :---: |
| **Branch Coverage** | **5,390 / 7,598** | **70.94%** | **> 65.00%** | **+5.94% (Pass)** |
| **Instruction Coverage** | **58,936 / 76,652** | **76.89%** | — | — |
| **Line Coverage** | **12,006 / 15,597** | **76.98%** | — | — |
| **Class Coverage** | **190 / 190** | **100.00%** | — | All classes evaluated |

---

## 3.2 Per-Package Coverage Breakdown

Extracted directly from `target/site/jacoco/jacoco.csv`:

| Package | Classes | Branch Covered / Total | Branch % | Line Covered / Total | Line % | Instruction % |
|---| :---: | :---: | :---: | :---: | :---: | :---: |
| `com.hkust.marketplace.services` | 37 | 2,352 / 2,645 | **88.92%** | 3,409 / 3,461 | 98.50% | 98.23% |
| `com.hkust.marketplace.models` | 111 | 1,408 / 1,616 | **87.13%** | 3,034 / 3,068 | 98.89% | 98.64% |
| `com.hkust.marketplace.database` | 1 | 266 / 320 | **83.12%** | 661 / 740 | 89.32% | 88.71% |
| `com.hkust.marketplace.utils` | 8 | 487 / 614 | **79.32%** | 1,091 / 1,169 | 93.33% | 95.53% |
| `com.hkust.marketplace.controllers.seller` | 8 | 367 / 743 | **49.39%** | 1,539 / 2,364 | 65.10% | 68.39% |
| `com.hkust.marketplace.controllers.admin` | 14 | 272 / 817 | **33.29%** | 1,133 / 2,460 | 46.06% | 46.85% |
| `com.hkust.marketplace.controllers.buyer` | 8 | 238 / 843 | **28.23%** | 1,139 / 2,309 | 49.33% | 54.12% |
| `com.hkust.marketplace` & `controllers` | 3 | 0 / 0 | 100.00% | 0 / 26 | 0.00% | 0.00% |
| **Total (Global)** | **190** | **5,390 / 7,598** | **70.94%** | **12,006 / 15,597** | **76.98%** | **76.89%** |

> [!NOTE]
> Core business-rule and persistence packages (`services`, `models`, `database`, `utils`) average **86.87% branch coverage** (`4,513 / 5,195`). Combined with headless JavaFX controller tests (`controllers.*`), global repository branch coverage reaches **70.94%** without any JaCoCo exclusions.

---

## 3.3 Verification Commands

```bash
# Run tests and regenerate JaCoCo XML/CSV/HTML report
./mvnw clean test

# Open HTML coverage report in browser
# File: target/site/jacoco/index.html

# Verify exact branch coverage percentage from CSV
python3 -c "
import csv
with open('target/site/jacoco/jacoco.csv') as f:
    rows = list(csv.DictReader(f))
bm = sum(int(r['BRANCH_MISSED']) for r in rows)
bc = sum(int(r['BRANCH_COVERED']) for r in rows)
print(f'Branch Coverage: {bc}/{bm + bc} ({bc/(bm + bc)*100:.2f}%)')
"
```
