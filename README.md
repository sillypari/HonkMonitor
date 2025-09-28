# Honk Monitor App 🚗📱

A minimal Android app that records microphone and GPS data to detect and count vehicle horn events on roads — built to visualize and quantify how much people honk in India.

## Features

- 🎤 **Real-time Horn Detection**: Uses Goertzel algorithm for on-device audio analysis
- 📍 **GPS Tracking**: Logs location data alongside horn events
- 🔄 **Foreground Service**: Continues monitoring in background
- 💾 **Local Storage**: Room database for offline data persistence
- 📊 **Statistics**: View total honks and recent events
- 📤 **Data Export**: CSV export functionality for analysis
- 🔐 **Privacy First**: No cloud uploads, all processing on-device

## Architecture

| Component | Responsibility |
|-----------|----------------|
| `MainActivity` | Start/Stop service, show stats |
| `MonitorForegroundService` | Runs audio + location monitoring |
| `HornDetector` | Detect horn events using Goertzel |
| `LocationTracker` | Track route using GPS |
| `HonkRepository` | Store events in Room database |

## Horn Detection Algorithm

The app uses the **Goertzel algorithm** to detect horn frequencies:

1. **Audio Sampling**: PCM audio at 16kHz, 16-bit mono
2. **Frame Processing**: 200ms frames for analysis
3. **Frequency Analysis**: Targets 300-2000Hz range (typical horn frequencies)
4. **Adaptive Threshold**: Compares band energy to noise baseline
5. **Event Logging**: Records timestamp, location, and confidence

### Target Frequencies
- 300Hz, 500Hz, 800Hz, 1200Hz, 1500Hz, 2000Hz

## Required Permissions

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO"/>
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION"/>
<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
```

## Setup Instructions

### Prerequisites
- Android Studio Arctic Fox or later
- Android SDK 24+ (Android 7.0)
- Google Play Services for Maps
- Device with microphone and GPS

### Installation

1. **Clone the repository**
   ```bash
   git clone https://github.com/yourusername/honk-monitor-app.git
   cd honk-monitor-app
   ```

2. **Open in Android Studio**
   - Open Android Studio
   - Select "Open an Existing Project"
   - Navigate to the cloned directory

3. **Configure Google Maps API**
   - Get a Google Maps API key from [Google Cloud Console](https://console.cloud.google.com/)
   - Replace `YOUR_GOOGLE_MAPS_API_KEY` in `AndroidManifest.xml`

4. **Build and Run**
   ```bash
   ./gradlew assembleDebug
   ./gradlew installDebug
   ```

## Usage

1. **Grant Permissions**: App will request microphone, location, and notification permissions
2. **Start Monitoring**: Tap "Start Monitoring" to begin detection
3. **Background Operation**: App runs in foreground service with persistent notification
4. **View Stats**: See total honks and recent events on main screen
5. **Export Data**: Use "Export CSV" to save detection data
6. **Stop Monitoring**: Tap "Stop Monitoring" or use notification action

## Database Schema

```kotlin
@Entity(tableName = "honk_events")
data class HonkEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,      // Unix timestamp
    val latitude: Double,     // GPS latitude
    val longitude: Double,    // GPS longitude
    val confidence: Double,   // Detection confidence (0-1)
    val audioLevel: Double    // RMS audio level
)
```

## Configuration

### Audio Settings
- **Sample Rate**: 16kHz (configurable in `HornDetector`)
- **Frame Size**: 200ms (adjustable for sensitivity)
- **Debounce**: 700ms (prevents duplicate detections)

### Detection Tuning
- **Threshold Multiplier**: 6.0x noise baseline (tune for environment)
- **Noise Adaptation**: 98% old + 2% current (slow adaptation)
- **Frequency Range**: 300-2000Hz (covers most vehicle horns)

## Privacy & Legal

- ✅ **User Consent**: Always shows permission requests
- ✅ **No Raw Audio Upload**: Only logs detection events
- ✅ **Local Processing**: All analysis done on-device
- ✅ **Transparent Operation**: Persistent notification when active
- ✅ **Data Control**: Users can export and clear their data

## Development

### Project Structure
```
app/src/main/java/com/honkmonitor/
├── MainActivity.kt                 # Main UI activity
├── audio/
│   ├── Goertzel.kt                # Frequency detection algorithm
│   └── HornDetector.kt            # Audio processing engine
├── data/
│   ├── HonkEvent.kt               # Data model
│   ├── HonkEventDao.kt            # Database access
│   ├── HonkDatabase.kt            # Room database
│   └── HonkRepository.kt          # Data repository
├── location/
│   └── LocationTracker.kt         # GPS location handling
└── service/
    └── MonitorForegroundService.kt # Background monitoring
```

### Testing
- Test on various Android versions (API 24+)
- Verify permissions handling
- Test background service behavior
- Validate horn detection accuracy
- Check battery optimization settings

### Performance Tips
- Monitor battery usage with background processing
- Optimize audio buffer sizes for device capabilities
- Consider adaptive sampling rates based on battery level
- Implement intelligent service scheduling

## Future Enhancements

- [ ] **Map Visualization**: Show route with horn markers
- [ ] **Statistics Dashboard**: Honks per km, hourly patterns
- [ ] **Machine Learning**: TensorFlow Lite model for improved detection
- [ ] **Speed Integration**: Correlate honking with traffic conditions
- [ ] **Community Features**: Anonymous aggregate data sharing
- [ ] **Real-time Visualization**: Live audio spectrum display

## Contributing

1. Fork the repository
2. Create feature branch (`git checkout -b feature/improvement`)
3. Commit changes (`git commit -m 'Add improvement'`)
4. Push to branch (`git push origin feature/improvement`)
5. Open Pull Request

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- Goertzel algorithm implementation for efficient frequency detection
- Google Play Services for location tracking
- Room database for robust local storage
- Material Design components for modern UI

## Support

For issues, questions, or contributions:
- 📧 Email: your.email@example.com
- 🐛 Issues: [GitHub Issues](https://github.com/yourusername/honk-monitor-app/issues)
- 📖 Documentation: [Wiki](https://github.com/yourusername/honk-monitor-app/wiki)

---

**Note**: This app is designed for research and awareness purposes. Please respect local laws regarding audio recording and ensure user privacy when collecting data.