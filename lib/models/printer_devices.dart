import 'dart:io';

/// By Xiao 2023/1

class BluetoothDevice {
  String name;
  String address;
  int rssi;

  /// Android Only, Device type, @see android.bluetooth.BluetoothDevice.
  /// In iOS, it's always null.
  BluetoothAndroidDeviceType? androidType;

  /// Android Only, @see android.bluetooth.BluetoothDevice
  /// In iOS, it's always null.
  int? androidDeviceClass;

  /// Android Only, @see android.bluetooth.BluetoothDevice
  /// In iOS, it's always null.
  int? androidMajorDeviceClass;

  /// Android Only, @see android.bluetooth.BluetoothDevice
  /// In iOS, it's always null.
  BluetoothAndroidBondState? androidBondState;

  /// iOS Only, CoreBluetooth.CBAdvertisementData
  /// In Android, it's always null.
  /// Format keys are defined in [BluetoothIOSAdvertisementDataKey]
  Map<String, String>? iOSAdvertisementData;

  bool get isLE {
    if (Platform.isIOS) return true;
    return androidType == BluetoothAndroidDeviceType.le || androidType == BluetoothAndroidDeviceType.dual;
  }

  BluetoothDevice(
      {required this.name,
      required this.address,
      required this.rssi,
      this.androidType,
      this.androidDeviceClass,
      this.androidMajorDeviceClass,
      this.androidBondState,
      this.iOSAdvertisementData});

  factory BluetoothDevice.fromMap(Map map) {
    return BluetoothDevice(
      name: map['name'] ?? "",
      address: map['address'] ?? "",
      rssi: map['rssi'] ?? -1,
      androidType: map.containsKey('type') ? BluetoothAndroidDeviceType.fromKey(map['type']) : null,
      androidDeviceClass: map['deviceClass'],
      androidMajorDeviceClass: map['majorDeviceClass'],
      androidBondState: map.containsKey('bondState') ? BluetoothAndroidBondState.fromKey(map['bondState']) : null,
      iOSAdvertisementData: map['advertisementData']?.cast<String, String>(),
    );
  }
}

enum BluetoothAndroidDeviceType {
  classic,
  le,
  dual,
  unknown;

  factory BluetoothAndroidDeviceType.fromKey(int key) {
    switch (key) {
      case 1:
        return BluetoothAndroidDeviceType.classic;
      case 2:
        return BluetoothAndroidDeviceType.le;
      case 3:
        return BluetoothAndroidDeviceType.dual;
      default:
        return BluetoothAndroidDeviceType.unknown;
    }
  }
}

enum BluetoothAndroidBondState {
  bonded,
  bonding,
  none;

  factory BluetoothAndroidBondState.fromKey(int key) {
    switch (key) {
      case 12:
        return BluetoothAndroidBondState.bonded;
      case 11:
        return BluetoothAndroidBondState.bonding;
      case 10:
        return BluetoothAndroidBondState.none;
      default:
        return BluetoothAndroidBondState.none;
    }
  }
}

enum BluetoothIOSAdvertisementDataKey {
  CBAdvertisementDataLocalNameKey("Local Name"),
  CBAdvertisementDataTxPowerLevelKey("Tx Power Level"),
  CBAdvertisementDataServiceUUIDsKey("Service UUIDs"),
  CBAdvertisementDataServiceDataKey("Service Data"),
  CBAdvertisementDataManufacturerDataKey("Manufacturer Data"),
  CBAdvertisementDataOverflowServiceUUIDsKey("Overflow Service UUIDs"),
  CBAdvertisementDataIsConnectable("Device is Connectable"),
  CBAdvertisementDataSolicitedServiceUUIDsKey("Solicited Service UUIDs");

  const BluetoothIOSAdvertisementDataKey(String name);
}
