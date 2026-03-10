# AroundMe

## Register / Sign-Up Flow

This project now includes a full MVVM register screen implemented with:
- `ConstraintLayout` + Material Components XML
- `Fragment` + ViewBinding
- `RegisterViewModel` + `AuthRepository`
- Firebase Authentication, Firestore, and Storage
- Google sign-up support
- Profile image selection from gallery or camera preview

## Firebase setup

Make sure your Firebase project is configured for:
- Authentication: Email/Password and Google
- Firestore: collection `Users`
- Storage: path `images/{userId}.jpg`

Also update `app/src/main/res/values/firebase_auth.xml` with a real `default_web_client_id` for Google sign-in.

## Build

Use the Gradle wrapper from the project root:

- `gradlew.bat assembleDebug`
- `gradlew.bat testDebugUnitTest`

## Main files added or updated

- `app/src/main/res/layout/fragment_register.xml`
- `app/src/main/java/com/colman/aroundme/RegisterFragment.kt`
- `app/src/main/java/com/colman/aroundme/auth/RegisterViewModel.kt`
- `app/src/main/java/com/colman/aroundme/auth/AuthRepository.kt`
- `app/src/main/java/com/colman/aroundme/auth/RegisterValidator.kt`
- `app/src/test/java/com/colman/aroundme/auth/RegisterViewModelTest.kt`
