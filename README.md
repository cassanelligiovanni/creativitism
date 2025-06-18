# App Redirector

An Android app that allows you to redirect one app to another. When you try to open an app that has a redirection set up, it will automatically open the target app instead.

## Features

- List all installed apps on your device
- Set up redirections from one app to another
- View and manage current redirections
- Remove redirections when no longer needed

## Setup Instructions

### 1. Configure Android SDK Path

1. Open the `local.properties` file in the root directory
2. Add your Android SDK path:
   ```
   sdk.dir=/Users/yourusername/Library/Android/sdk
   ```
   (Replace with your actual Android SDK path)

### 2. Open in Android Studio

1. Launch Android Studio
2. Click "Open an existing Android Studio project"
3. Navigate to this project folder and select it
4. Wait for Gradle sync to complete

### 3. Build and Run

1. Connect your Android device via USB or set up an emulator
2. Enable USB Debugging on your device (Settings > Developer Options > USB Debugging)
3. Click the "Run" button (green play icon) in Android Studio
4. Select your device and click "OK"

### 4. Grant Permissions

When you first run the app, you may need to grant certain permissions:

1. **Query All Packages**: This allows the app to see all installed applications
2. Go to Settings > Apps > App Redirector > Permissions to manage permissions

## How to Use

### Setting Up Redirections

1. Open the App Redirector app
2. Scroll through the list of installed apps
3. Find the app you want to redirect FROM
4. Tap "Set Redirection" button next to it
5. Select the target app you want to redirect TO
6. The redirection is now set up!

### Managing Redirections

- View all current redirections at the top of the screen
- Tap "Remove Redirection" to delete a redirection
- Redirections are saved automatically

## Important Notes

### Limitations

This app has some important limitations due to Android's security model:

1. **Manual Redirection**: Currently, the app doesn't automatically intercept app launches. You need to manually open the App Redirector app and set up redirections.

2. **No Automatic Interception**: Due to Android's security restrictions, apps cannot automatically intercept other app launches without being set as the default launcher.

3. **Launcher Replacement Required**: For true automatic redirection, this app would need to be set as your default launcher, which would replace your current home screen.

### Future Improvements

To make this app fully functional with automatic redirection, you would need to:

1. Implement a full launcher interface
2. Set the app as the default launcher
3. Handle all home screen functionality
4. Implement the redirection logic in the launcher

## Technical Details

The app is built with:
- **Language**: Kotlin
- **UI Framework**: Android Views with Material Design 3
- **Architecture**: MVVM pattern with ViewBinding
- **Storage**: SharedPreferences for redirection data
- **Target SDK**: Android 14 (API 34)
- **Minimum SDK**: Android 5.0 (API 21)

## File Structure

```
app/
├── src/main/
│   ├── java/com/creativitism/appredirector/
│   │   ├── MainActivity.kt          # Main app interface
│   │   ├── RedirectionActivity.kt   # Handles redirection logic
│   │   ├── AppInfo.kt              # Data class for app information
│   │   ├── RedirectionInfo.kt      # Data class for redirections
│   │   ├── AppListManager.kt       # Manages installed apps
│   │   ├── RedirectionManager.kt   # Manages redirection storage
│   │   ├── AppsAdapter.kt          # RecyclerView adapter for apps
│   │   └── RedirectionsAdapter.kt  # RecyclerView adapter for redirections
│   ├── res/
│   │   ├── layout/                 # XML layout files
│   │   ├── values/                 # Strings, colors, themes
│   │   └── xml/                    # Backup and data extraction rules
│   └── AndroidManifest.xml         # App configuration and permissions
├── build.gradle                    # App-level build configuration
└── proguard-rules.pro             # Code obfuscation rules
```

## License

This project is for educational purposes. Feel free to modify and use as needed. 