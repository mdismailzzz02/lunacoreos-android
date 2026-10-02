# LunaCoreOS Android 📱

Welcome to the official Android companion app for **LunaCoreOS** — the personal, privacy-first, centralized productivity suite.

This app is designed to seamlessly sync your digital life between your phone and your LunaCoreOS desktop environment, acting as an extension of your primary workspace. 

## ✨ Key Features

- **📦 Linkbox & Bookmarks:** Instantly save URLs, articles, and text directly from any app (like Chrome or Twitter) straight to your LunaCore Linkbox via the native Android Share Sheet.
- **📋 Universal Clipboard:** Send text from your phone directly to your desktop clipboard, and vice versa. Includes a Quick Settings tile for lightning-fast clipboard saving from anywhere on Android!
- **🖼️ Media Vault:** Securely browse, view, and organize your photos and videos backed up to your personal Supabase cloud. Features a sleek, modern gallery grid.
- **📝 Distraction-Free Writing:** A mobile-optimized, rich-text (WYSIWYG) editor with automatic syncing to your desktop's Long-form Writing section. Never lose a thought with seamless auto-saving.
- **🔒 Secret Vaults:** Every feature (Linkbox, Clipboard, Media, and Writing) includes an encrypted, PIN-locked "Secret Mode." Keep your sensitive data entirely invisible from the main app interface. (Hint: Try tapping the header text 3 times fast!).
- **🔄 Zero-RAM Background Sync:** (Via LunaSync integration) Automatically backs up your `DCIM`, `Pictures`, `Documents`, and `Downloads` folders in the background using minimal battery and memory.

## 🎨 Design Philosophy

LunaCore Android is built with a **premium, modern aesthetic** in mind. 
- Deep, immersive dark mode by default (`bg_dark` & `bg_surface`).
- Beautiful, highly-rounded Material Design 3 dialogs.
- Fluid animations and carefully chosen typography.

## 🚀 Getting Started

1. Clone this repository.
2. Open the project in **Android Studio**.
3. Ensure your `SupabaseClient` is properly configured with your unique project URL and API keys (typically handled via SharedPreferences upon initial login).
4. Build and run on any Android device running Android 8.0+.

## 🛠️ Tech Stack

- **Language:** Java
- **UI:** Android XML / Material Components
- **Backend:** Supabase (Auth, Database, Storage)
- **Architecture:** Native Android SDK (Activities, Fragments, ViewModels)

---
*Your data. Your workspace. Everywhere you go.*
