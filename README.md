# Novel Reader – Minimalist, Private, Cloud-Synced eBook Reader for Android

A completely open-source, clean, and privacy-first novel reader built with **Kotlin + Jetpack Compose + Firebase**.  
Perfect for reading your personal `.txt` novel collections with seamless cloud progress sync.



### Features
- 100% offline reading – Import any `.txt` file via Storage Access Framework
- Cloud progress sync across devices (Firebase Firestore)
- Fully customizable reading experience: font size, line height, night mode
- Pure Material 3 design with Jetpack Compose
- No ads, no analytics, no unnecessary permissions
- Batch management: search, rename, delete
- Avatar, password reset, account deletion
- Fast adaptive pagination even for huge files

### Tech Stack
- Language: 100% Kotlin
- UI: Jetpack Compose + Material 3
- Architecture: MVVM + Repository
- Auth & Sync: Firebase Authentication + Cloud Firestore
- File Access: SAF with persistent URI permissions


### Screenshots

| Library                  | Reader (Light)                 | Reader (Dark)                  | Settings                       |
|--------------------------|--------------------------------|--------------------------------|--------------------------------|
| <img src="https://github.com/user-attachments/assets/2b34a2c7-9691-4595-8ce1-917ce1161ca6" width="240x520" style="border-radius:12px"/> | <img src="https://github.com/user-attachments/assets/6236afd4-b8ea-4fc1-9499-e786fb66c696" width="240" style="border-radius:12px"/> | <img src="https://github.com/user-attachments/assets/1a6b4fc1-c28c-4508-b6f0-a93064f3a72a" width="240" style="border-radius:12px"/> | <img src="https://github.com/user-attachments/assets/ef28df22-dba1-4194-a2ff-ddab6e6126ca" width="240" style="border-radius:12px"/> |

### Setup

```bash
git clone https://github.com/3621574947/NovelReaderApp.git
cd novel-reader
```
Open with Android Studio Iguana (2023.2.1+)
Set up Firebase:
https://console.firebase.google.com
Add Android app (package: com.ningyu.novelreader)
Place google-services.json in app/
Enable Email/Password sign-in + create Firestore (test mode OK)

Run!

Works fully offline after first login.
Privacy

Only accesses files you explicitly choose
No analytics, no tracking
All cloud data lives in your own Firebase project

Contributing
Welcome! Feel free to:

Report bugs / request features
Improve UI/UX
Add EPUB/PDF support
Translate

License
MIT License © 2025 Ningyu
See LICENSE file for details
Happy reading!
