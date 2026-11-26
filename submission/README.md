# HKUST Marketplace — Assessment & Submission Deliverables (COMP 3111 Group 27)

**Team:** Group 27 (`Elio Le Dortz`, `Charles Dupouy`, `Antoine Lepin`)  
**Tech Stack:** Java 17 · JavaFX 21 · Maven Wrapper (`./mvnw`) · Embedded SQLite (`sqlite-jdbc`) · JUnit 5 · JaCoCo

Welcome to the assessment deliverables hub for **HKUST Marketplace**. All **28 use cases** across the Buyer, Seller, and Administrator portals are implemented, integrated, and verified.

---

## Linked Submission Documents

| # | Required Deliverable | Document Link | Status / Summary |
| :---: | :--- | :--- | :--- |
| **1** | **Application Execution Screenshots** *(sample inputs & outputs)* | [**Application Execution & Screenshots Guide**](01_application_execution_screenshots.md) | 8 live JavaFX screenshots captured and embedded across Buyer, Seller, and Admin portals. |
| **2** | **Unit Testing Report** *(100% pass)* | [**Unit Testing Report**](02_unit_testing_report.md) | **2,008 / 2,008 tests passing** (`0` failures, `0` errors, `0` skipped) across **94 test files**. |
| **3** | **Coverage Test Report** *(>65% branch coverage)* | [**JaCoCo Code Coverage Report**](03_coverage_report.md) | **70.94% global branch coverage** (`5,390 / 7,598`), **76.98% line**, **100% class** (`190 / 190`) with zero JaCoCo exclusions. |
| **4** | **JavaDoc Documentation** | [**JavaDoc API Documentation Guide**](04_javadoc_documentation.md) | Full class/method/enum Javadoc with HTML5 doclint compliance (`./mvnw javadoc:javadoc`). |
| **5** | **Supplementary Notes** *(WoW factors, algorithms, assumptions)* | [**Supplementary Notes**](05_supplementary_notes.md) | System architecture, WoW innovations, algorithm & concurrency analyses, assumptions/limitations, and TA demo accounts. |

---

## Quick Verification Commands

```bash
# 1. Run all 2,008 unit & UI tests + generate JaCoCo coverage report (target/site/jacoco/index.html)
./mvnw clean test

# 2. Generate JavaDoc API HTML pages (target/site/apidocs/index.html)
./mvnw javadoc:javadoc

# 3. Launch the JavaFX Desktop Application
./mvnw clean javafx:run
```

> Return to the main project guide: [**Project README**](../README.md)
