# flutter_simple_bluetooth_printer
A library to discover printers, connect and send printer commands.

Inspired by:   
[flutter_pos_printer](https://pub.flutter-io.cn/packages/flutter_pos_printer_platform)   
[bluetooth_print](https://pub.flutter-io.cn/packages/bluetooth_print)   
[flutter_bluetooth_serial](https://pub.flutter-io.cn/packages/flutter_bluetooth_serial)   
[flutter_blue](https://pub.flutter-io.cn/packages/flutter_blue)   

Based on:  
[rxandroidble](https://github.com/dariuszseweryn/RxAndroidBle) - Android  
[lightBlue](https://github.com/Pluto-Y/Swift-LightBlue) - iOS
## Features

|                       |      Android       |        iOS         | Description                                               |
|:----------------------| :----------------: |:------------------:|:----------------------------------------------------------|
| classic bluetooth     | :white_check_mark: |                    | Support Connect/Print classic bluetooth devices           |
| get paired devices    | :white_check_mark: |                    | Get paried devices of Phone.                              |
| discovery             | :white_check_mark: | :white_check_mark: | Scanning for Bluetooth Low Energy devices.                |
| stop discovery        | :white_check_mark: | :white_check_mark: | Stop scanning for Bluetooth Low Energy devices.           |
| scan                  | :white_check_mark: | :white_check_mark: | Scan for Bluetooth LE devices until timeout is reached.   |
| connect               | :white_check_mark: | :white_check_mark: | Establishes a connection to the device.                   |
| disconnect            | :white_check_mark: | :white_check_mark: | Cancels an connection to the device.                      |
| connect state         | :white_check_mark: | :white_check_mark: | Stream of connect state changes for the Bluetooth Device. |
| current connect state | :white_check_mark: | :white_check_mark: | Get the lastest state of connect state.                   |
| write text            | :white_check_mark: | :white_check_mark: | write text to the connected device.                       |
| write raw data        | :white_check_mark: | :white_check_mark: | write Uint8List data to the connected device.             |

## Permissions

### Android
All required permissions are included in the library.  
Including:  
```xml
<!-- Request legacy Bluetooth permissions on older devices. -->
<uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />

<!-- For Android6(API23)-Android11(API30) -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />

<!-- For Android12(API31)+ -->
<!--@see https://developer.android.google.cn/about/versions/12/features/bluetooth-permissions-->
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
```
See https://github.com/dariuszseweryn/RxAndroidBle for details.  
This plugin will handle Runtime permissions request.

### iOS
Add permissions to **ios/Runner/Info.plist**:

```xml 
	    <key>NSBluetoothAlwaysUsageDescription</key>  
	    <string>Replace with your description here</string>  
	    <key>NSBluetoothPeripheralUsageDescription</key>  
	    <string>Replace with your description here</string>  
	    <key>NSLocationAlwaysAndWhenInUseUsageDescription</key>  
	    <string>Replace with your description here</string>  
	    <key>NSLocationAlwaysUsageDescription</key>  
	    <string>Replace with your description here</string>  
	    <key>NSLocationWhenInUseUsageDescription</key>  
	    <string>Replace with your description here</string>
	    <key>UIBackgroundModes</key>  
        <array>
           <string>bluetooth-central</string>
           <string>bluetooth-peripheral</string>
        </array>
```

### Discovery & Stop discovery
```dart
try{
  var subsription = bluetoothManager.discovery().listen((device) {
    // A new device is discovered.
    devices.add(device);
  });
  Future.delayed(Duration(seconds: 20)).then((_) {
    // Remember to stop discovery after use.
    bluetoothManager.stopDiscovery();
    subsription.cancel();
  });
} on BTException catch(e){
  print(e);
}
```
Or listen to scanResults to get the full list of devices.
```dart
try{
  bluetoothManager.discovery();
  var subsription = bluetoothManager.scanResults.listen((list) {
    // Every time a new device is discovered, the full list of devices is callback.
    devices = list;
  });
  Future.delayed(Duration(seconds: 20)).then((_) {
    // Remember to stop discovery after use.
    bluetoothManager.stopDiscovery();
    subsription.cancel();
  });
} on BTException catch(e){
  print(e);
}
```

### Scan
Similar to Discovery, but stop automatically after timeout is reached.
```dart
try{
  final devices = await bluetoothManager.scan(timeout: const Duration(seconds: 10));
} on BTException catch(e){
  print(e);
}
```

### Connect
```dart
try {
  var _isConnected = await bluetoothManager.connect(address: selectedPrinter.address);
} on BTException catch (e) {
  print(e);
}
```

### Print (by Text or Unit8List)
```dart
try {
  if (!await connect()) return; // Make sure connected to device before print
  final isSuccess = await bluetoothManager.writeText(text);
  // Or print by Unit8List
  // final isSuccess = await bluetoothManager.writeRawData(codes);
} on BTException catch (e) {
  print(e);
}
```

### Disconnect
```dart
try {
  await bluetoothManager.disconnect();
} on BTException catch (e) {
  print(e);
}
```

### Listen to connect state
```dart
var _subscriptionBtStatus = bluetoothManager.connectState.listen((status) {
  print('$status');
});
```

### Get Paired Devices (Android Only)
```dart
try {
  final bondedDevices = await bluetoothManager.getAndroidPairedDevices();
} on BTException catch (e) {
  print(e);
}
```

---  
See [example](example/lib/main.dart) for full example.
