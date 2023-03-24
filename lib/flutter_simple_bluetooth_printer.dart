import 'dart:typed_data';

import 'package:flutter_simple_bluetooth_printer/models/bt_state.dart';
import 'package:flutter_simple_bluetooth_printer/models/connect_state.dart';
import 'package:flutter_simple_bluetooth_printer/models/connection_config.dart';
import 'package:flutter_simple_bluetooth_printer/models/printer_devices.dart';
import 'flutter_simple_bluetooth_printer_platform_interface.dart';

export 'package:flutter_simple_bluetooth_printer/models/bt_error.dart';
export 'package:flutter_simple_bluetooth_printer/models/bt_state.dart';
export 'package:flutter_simple_bluetooth_printer/models/connect_state.dart';
export 'package:flutter_simple_bluetooth_printer/models/printer_devices.dart';
export 'package:flutter_simple_bluetooth_printer/models/connection_config.dart';

/// By Xiao 2023/1
class FlutterSimpleBluetoothPrinter {
  FlutterSimpleBluetoothPrinter._();

  static const int minAndroidMTU = 23;
  static const int maxAndroidMTU = 515;

  static final FlutterSimpleBluetoothPrinter _instance = FlutterSimpleBluetoothPrinter._();

  static FlutterSimpleBluetoothPrinter get instance => _instance;

  /// Get the current bluetooth state
  Future<BTState> getBluetoothState() async {
    return await FlutterSimpleBluetoothPrinterPlatform.instance.getBluetoothState();
  }

  /// Get paired devices for Android.
  /// For iOS, it will return an empty list.
  /// Throw [BTException] if failed.
  Future<List<BluetoothDevice>> getAndroidPairedDevices() {
    return FlutterSimpleBluetoothPrinterPlatform.instance.getAndroidPairedDevices();
  }

  /// Get the Discovery result stream.
  Stream<List<BluetoothDevice>> get scanResults => FlutterSimpleBluetoothPrinterPlatform.instance.scanResults;

  /// Starts scan for Bluetooth LE devices
  /// Note: This is a continuous behavior, don't forget to call [stopDiscovery].
  /// Throw [BTException] if failed.
  Stream<BluetoothDevice> discovery() {
    return FlutterSimpleBluetoothPrinterPlatform.instance.discovery();
  }

  /// Stops scan for Bluetooth LE devices.
  /// Throw [BTException] if failed.
  Future<bool> stopDiscovery() {
    return FlutterSimpleBluetoothPrinterPlatform.instance.stopDiscovery();
  }

  /// Scan for Bluetooth LE devices until [timeout] is reached.
  /// Throw [BTException] if failed.
  Future<List<BluetoothDevice>> scan({required Duration timeout}) {
    return FlutterSimpleBluetoothPrinterPlatform.instance.scan(timeout: timeout);
  }

  /// Connect to a Bluetooth device via address.
  /// [config] The connection config. Default to [LEBluetoothConnectionConfig].
  /// Throw [BTException] if failed.
  Future<bool> connect({required String address, ConnectionConfig config = const LEBluetoothConnectionConfig()}) {
    return FlutterSimpleBluetoothPrinterPlatform.instance.connect(address: address, config: config);
  }

  /// Disconnect from a Bluetooth device.
  /// Return bool as result.
  /// Throw [BTException] when error.
  /// [delay] The delay time before disconnect, default to 0.
  Future<bool> disconnect({Duration delay = Duration.zero}) {
    return FlutterSimpleBluetoothPrinterPlatform.instance.disconnect(delay: delay);
  }

  /// Setup notification for a characteristic, LE only.
  /// * Must connect to a device first.
  /// Return bool as result.
  /// Throw [BTException] when error.
  Future<bool> setupNotification({required String characteristicUuid}) {
    return FlutterSimpleBluetoothPrinterPlatform.instance.setupNotification(characteristicUuid: characteristicUuid);
  }

  /// Setup indication for a characteristic, LE only.
  /// * Must connect to a device first.
  /// Return bool as result.
  /// Throw [BTException] when error.
  Future<bool> setupIndication({required String characteristicUuid}) {
    return FlutterSimpleBluetoothPrinterPlatform.instance.setupIndication(characteristicUuid: characteristicUuid);
  }

  /// Request MTU, LE only. The mtu should be between 23 and 515.
  /// * Must connect to a device first.
  /// Return the mtu as result.
  /// Throw [BTException] when error.
  Future<int> requestMtu({required int mtu}) {
    return FlutterSimpleBluetoothPrinterPlatform.instance.requestMtu(mtu);
  }

  /// Get the connection state stream.
  Stream<BTConnectState> get connectState => FlutterSimpleBluetoothPrinterPlatform.instance.connectState;

  /// Check is connected to a device.
  /// If the previous connection is waiting for disconnect(See the delay of [disconnect]), this will stop the disconnect and return true.
  /// [isLE] If true, will check the LE connection state, otherwise will check the classic connection state. For iOS,
  /// will ignore this parameter cause we only support LE on iOS.
  /// Throw [BTException] when error.
  Future<bool> ensureConnected({required String address, bool isLE = true}) {
    return FlutterSimpleBluetoothPrinterPlatform.instance.ensureConnected(address: address, isLE: isLE);
  }

  /// Write text to the connected device.
  /// * Must connect to a device first.
  /// Return bool as result.
  /// Throw [BTException] when error.
  /// [characteristicUuid] Only BLE, specific the characteristic to write. If null, will use the first writable characteristic.
  Future<bool> writeText(String text, {String? characteristicUuid}) {
    return FlutterSimpleBluetoothPrinterPlatform.instance.writeText(text, characteristicUuid: characteristicUuid);
  }

  /// Write raw data to the connected device.
  /// * Must connect to a device first.
  /// Return bool as result.
  /// Throw [BTException] when error.
  /// [characteristicUuid] Only BLE, specific the characteristic to write. If null, will use the first writable characteristic.
  Future<bool> writeRawData(Uint8List bytes, {String? characteristicUuid}) {
    return FlutterSimpleBluetoothPrinterPlatform.instance.writeRawData(bytes, characteristicUuid: characteristicUuid);
  }
}
