# AI Expense Backend Lab

An Android experiment comparing SaaS and BYOK backend architectures while adding AI-powered features to an existing expense tracker application.

## Project Purpose

This project evaluates the developer and user experience of adding backend and AI capabilities to an existing Android app.

The primary comparison covers:

* Firebase AI Logic for a SaaS architecture
* Supabase Edge Functions for a BYOK architecture
* Initial development effort
* Long-term maintenance effort
* User onboarding and convenience
* Large-input processing performance
* AI usage, latency, reliability, and cost

## Attribution

This repository is a fork of [furqanullah717/expense-tracker-android](https://github.com/furqanullah717/expense-tracker-android).

The original expense-tracking functionality and project structure were created by [furqanullah717](https://github.com/furqanullah717). This fork adds experimental backend and AI features for educational and presentation purposes.

## Original App Features

* Add daily expenses
* View expenses by date and category
* Edit and delete saved expenses
* Analyze spending through statistics and charts
* Store expense data locally with Room

## Planned AI Features

* Register expenses using natural-language input
* Analyze monthly and long-term spending patterns
* Detect unusual expenses
* Recommend category-specific budgets
* Generate personalized spending insights
* Compare raw-data and preprocessed-data AI requests

## Architecture Experiments

### Firebase SaaS

```text
Android App
→ Firebase AI Logic
→ Gemini
→ AI Result
```

The developer manages the AI service and pays the associated API costs. Users can access AI features without registering their own API keys.

### Supabase BYOK

```text
Android App
→ Supabase Edge Function
→ Gemini using the user's API key
→ AI Result
```

Each user registers a personal Gemini API key and pays the AI usage cost through the corresponding account.

## Evaluation Areas

### Developer Experience

* Time required to receive the first AI response
* Total implementation time
* Number of setup steps
* Android and server code changes
* Build and deployment attempts
* Errors and troubleshooting time
* Subjective developer fatigue

### Maintainability

* Model and prompt replacement effort
* Number of files affected by a feature change
* Logging and monitoring support
* Authentication and rate-limit management
* API key rotation and deletion
* Deployment and rollback effort
* Backend platform dependency

### User Convenience

* Time required to use the first AI feature
* Number of onboarding steps
* API key registration difficulty
* Request success rate
* Error recovery time
* Result clarity and editing convenience
* Perceived privacy and cost transparency

### Performance

* Input transaction count
* Payload size
* Input and output tokens
* Serialization and preprocessing time
* Time to first response
* Total response time
* Memory usage
* Failure rate
* Cost per request
* Accuracy compared with Room or SQL calculations

## Technologies

* Kotlin
* Jetpack Compose
* Room Database
* Dagger Hilt
* MVVM Architecture
* Firebase AI Logic
* Firebase Cloud Functions
* Supabase Edge Functions
* Gemini API

## Screenshots

The following screens come from the original Expense Tracker Android application.

| Home Screen                                           | Add Expense                                           | Stats                                           |
| ----------------------------------------------------- | ----------------------------------------------------- | ----------------------------------------------- |
| ![Home Screen](screenshots/Screenshot_1724273822.png) | ![Add Expense](screenshots/Screenshot_1724273829.png) | ![Stats](screenshots/Screenshot_1724273956.png) |

## Getting Started

### Prerequisites

* Android Studio Bumblebee or later
* Java 11 or later
* Android SDK 21 or later

### Installation

1. Clone this repository.

```bash
git clone https://github.com/<your-github-username>/AI-Expense-Backend-Lab.git
```

2. Open the project in Android Studio.

3. Sync the project with the Gradle files.

4. Run the app on an emulator or physical Android device.

Backend configuration instructions will be added separately for each experiment branch.

## Branch Structure

```text
master
└─ Original expense tracker with common modifications

feature/ai-common
└─ Shared AI UI, domain models, and AiGateway interface

experiment/firebase-saas
└─ Firebase AI Logic SaaS implementation

experiment/firebase-byok
└─ Firebase Cloud Functions BYOK implementation

experiment/supabase-byok
└─ Supabase Edge Function BYOK implementation
```

## Original Tutorial

The original project was developed as part of the CodeWithFK YouTube tutorial series.

1. [Part 1: Project Setup and Basics](https://youtu.be/LfHkAUzup5E)
2. [Part 2: Implementing Room Database](https://youtu.be/dPeSoNWVu-Y)
3. [Part 3: Adding and Displaying Expenses](https://youtu.be/mq8lekRbF4I)
4. [Part 4: Tracking Expenses with Stats](https://youtu.be/xolI_2svC6w)

Original creator:

* GitHub: [furqanullah717](https://github.com/furqanullah717)
* YouTube: [CodeWithFK](https://www.youtube.com/@codewithfk)
* Email: [furqanullah717@gmail.com](mailto:furqanullah717@gmail.com)

## Project Status

This project is currently under development for an educational backend architecture comparison. AI features, backend configurations, and experiment results may change during implementation.
