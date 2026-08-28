# Rename Application to "Smart Spend AI"

This plan outlines the steps to rename the application from "Expense Tracker Android" to "Smart Spend AI", including rebranding the package name from `com.codewithfk.expensetracker.android` to `com.smartspend.ai`.

## Proposed Changes

### Configuration Files

#### [MODIFY] [strings.xml](file:///C:/Users/baejunsung/AndroidStudioProjects/expense-tracker-android/app/src/main/res/values/strings.xml)
- Change `app_name` to "Smart Spend AI".

#### [MODIFY] [settings.gradle.kts](file:///C:/Users/baejunsung/AndroidStudioProjects/expense-tracker-android/settings.gradle.kts)
- Change `rootProject.name` to "Smart Spend AI".

#### [MODIFY] [build.gradle.kts](file:///C:/Users/baejunsung/AndroidStudioProjects/expense-tracker-android/app/build.gradle.kts)
- Update `namespace` and `applicationId` to `com.smartspend.ai`.

#### [MODIFY] [AndroidManifest.xml](file:///C:/Users/baejunsung/AndroidStudioProjects/expense-tracker-android/app/src/main/AndroidManifest.xml)
- Update theme references and package-relative class names if necessary.

### Source Code Refactoring

#### [RENAME] Directory Structure
- Move all files from `app/src/main/java/com/codewithfk/expensetracker/android/` to `app/src/main/java/com/smartspend/ai/`.
- Move all files from `app/src/test/java/com/codewithfk/expensetracker/android/` to `app/src/test/java/com/smartspend/ai/`.
- Move all files from `app/src/androidTest/java/com/codewithfk/expensetracker/android/` to `app/src/androidTest/java/com/smartspend/ai/`.

#### [MODIFY] Package Declarations & Imports
- Update `package com.codewithfk.expensetracker.android` (and subpackages) to `package com.smartspend.ai`.
- Update all imports referencing the old package.

### Branding Refinement (Optional but recommended)

#### [MODIFY] [Theme.kt](file:///C:/Users/baejunsung/AndroidStudioProjects/expense-tracker-android/app/src/main/java/com/codewithfk/expensetracker/android/ui/theme/Theme.kt)
- Rename `ExpenseTrackerAndroidTheme` to `SmartSpendAITheme`.
- Rename `Theme.ExpenseTrackerAndroid` style in XML if applicable.

## Verification Plan

### Automated Tests
- Run `./gradlew assembleDebug` to ensure the project builds correctly with the new package name.
- Run unit tests and instrumented tests.

### Manual Verification
- Deploy the app to the device and verify the app name on the home screen.
- Verify the app functions correctly with the new package name.
