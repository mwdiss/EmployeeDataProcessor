# 📈 Employee Data Processor

<p align="center">
  <img src="https://img.shields.io/badge/Language-Java_17+-brightgreen" alt="Java Badge"/>
  <img src="https://img.shields.io/badge/GUI-Swing_ (FlatLaf)-blue" alt="Swing Badge"/>
  <img src="https://img.shields.io/badge/Architecture-Functional_Streams-red" alt="Functional Badge"/>
  <img src="https://img.shields.io/badge/Build-Runnable_JAR-yellow" alt="JAR Badge"/>
</p>

## ✨ Introduction

The **Employee Data Processor** is a lightweight yet powerful Java Swing application for instantly analyzing and exploring CSV datasets. It provides an interactive interface to load, filter, and transform data in real-time, leveraging a modern, performance-oriented design.

No setup required. Just download the JAR and run! 🚀

---

## 📸 Application Demo
*(A GIF of the application in action would be perfect here to showcase its features visually)*
`![Demo GIF of Employee Data Processor](path/to/demo.gif)`

---

## 🌟 Key Features

| Emoji | Feature                   | Explanation                                                                                                                                                             |
| :---: | :------------------------ | :---------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 📁 | **Robust CSV Ingestion**  | Securely loads CSV files up to 10MB, with automatic fallback logic to handle various character encodings (UTF-8, ISO-8859-1, etc.).                                   |
| 🔬 | **Advanced Dynamic Filter** | Combine multiple criteria: **numerical ranges** (Age, Salary), **auto-detected categories** (Department, Gender), and full-text search with **exact whole-word matching**.   |
| 📊 | **On-the-Fly Analytics**    | Calculates real-time averages for any numerical column on the filtered dataset. Cells are **color-coded** (red/green) against the average for instant visual insights.    |
| 👓 | **Customizable Viewport**   | A dedicated dialog to **show, hide, and reorder** all data columns on the fly, allowing for a personalized and clutter-free view of the data.                             |
| 🧑‍💻 | **Context-Aware Details**   | Double-click any row for a formatted summary, intelligently built from available columns (e.g., *"FirstName LastName, Title from Department"*).                            |

---

## ⚙️ Core Technology & Design

This project is built from the ground up using a modern, functional approach in Java to ensure high performance and maintainable code.

*   **Stream-Centric Architecture:** The entire data processing engine is built on the Java Streams API. All operations—from filtering and transformation to aggregation (`average()`)—are implemented as **lazy-evaluated stream pipelines**. This minimizes memory overhead and maximizes efficiency by processing data only when required.

*   **Functional Building Blocks:** We leverage `java.util.function` interfaces, like `Function<T, R>`, as the core components for these pipelines. They enable clean, reusable, and composable transformation logic, such as mapping an `Employee` object to a formatted display string.

*   **Immutable Data Models:** Data is modeled using modern, immutable Java `records`. This approach integrates seamlessly with the functional stream paradigm, enhances thread safety, and contributes to overall application stability.

---

## 💻 Getting Started

**Prerequisites:**
*   Java 17 or higher installed on your system.

**Running the application:**

1.  Navigate to the latest [**Release**](https://github.com/mwdiss/EmployeeDataProcessor/releases/tag/latest).
2.  Download the runnable `EmployeeDataProcessor.jar` from the assets.
3.  Double-click the `.jar` file to run, or execute from your terminal
    ```sh
    java -jar EmployeeDataProcessor.jar
    ```
