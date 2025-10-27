# 📈 Employee Data Processor

<p align="center">
  <img src="https://img.shields.io/badge/Language-Java-brightgreen" alt="Java Badge"/>
  <img src="https://img.shields.io/badge/GUI-Swing-blue" alt="Swing Badge"/>
  <img src="https://img.shields.io/badge/File%20Support-CSV-yellow" alt="CSV Badge"/>
  <img src="https://img.shields.io/badge/Style-Functional%20Streams-red" alt="Functional Badge"/>
</p>

## ✨ Introduction

The **Employee Data Processor** is a lightweight, modern Java application designed for rapidly analyzing and filtering large CSV employee datasets. Load your file, instantly view the data, apply advanced filters, manage column visibility, and get automatic salary analysis!

No complex setup. Just run the JAR and go! 🚀

---

## 🌟 Key Features

| Emoji | Feature | Explanation |
| :---: | :--- | :--- |
| 📁 | **CSV Loading** | Loads standard CSV files up to **10MB** with dynamic encoding selection. |
| 🔍 | **Intelligent Search** | Quick Search across visible columns supports both regular (`contains`) and exact quoted (`"value"`) matches. |
| 🔬 | **Advanced Filtering** | Apply multiple, granular filters: range filtering for **numerical** data (Age, Salary), and smart combo boxes for **categorical** columns (Department, Gender). |
| 📊 | **Smart Calculations** | Automatically identifies all salary/compensation columns (e.g., *MonthlyPay*, *AnnualBonus*) and calculates their average across the visible, filtered data, applying visual **color-coding** per column average. |
| 👓 | **Column Management** | Easily hide, show, and **reorder** your columns using the toggle dialog, reducing screen clutter. |
| 🧑‍💻 | **Dynamic Details** | Double-click any row to view employee details formatted using intelligent header detection (*Name, Role from Department*). |

---

## 💡 Functional Programming Principles

This program's design explicitly addresses key functional programming and Stream API requirements:

1.  **Core Data Model:** The employee dataset is read and stored in a standard **Java Collection** (`List<Employee>`).
2.  **Function Interface:** A `java.util.function.Function` interface implementation is used internally for data transformations, specifically to convert an `Employee` object into a concise display string (Name and Department concatenation) for detail pop-ups.
3.  **Data Processing with Streams:** All major data manipulation—including generating derived collections (like the employee name/department list), filtering records (generalized age/range criteria via filter composition), and advanced data aggregation—is performed using the powerful Java Stream API and its built-in functional components (`.map()`, `.filter()`, `.collect()`, and stream's built-in function to find **average salary**).

The `Function` interface acts as a fundamental abstraction representing a mathematical function: accepting an input (`Employee` object) and producing a definite output (a formatted `String` or another transformation), facilitating decoupled and reusable data pipeline components.

---

## 💻 Getting Started (Minimal Setup)

You don't need an IDE or any Java configuration; just follow these three easy steps:

1. Go to the latest [**Release**](link/to/your/releases).
2. Download the runnable `EmployeeDataProcessor.jar` file.
3. Double click to run.

(Note: You must have Java installed for this command to work.)
