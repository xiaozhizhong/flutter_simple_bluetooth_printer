import 'dart:typed_data';

import 'package:flutter_simple_bluetooth_printer/models/connect_state.dart';
import 'package:flutter_simple_bluetooth_printer/models/printer_devices.dart';
import 'flutter_simple_bluetooth_printer_platform_interface.dart';

export 'package:flutter_simple_bluetooth_printer/models/BTError.dart';
export 'package:flutter_simple_bluetooth_printer/models/connect_state.dart';
export 'package:flutter_simple_bluetooth_printer/models/printer_devices.dart';

/// By Xiao 2023/1
class FlutterSimpleBluetoothPrinter {
  FlutterSimpleBluetoothPrinter._();

  static final FlutterSimpleBluetoothPrinter _instance = FlutterSimpleBluetoothPrinter._();

  static FlutterSimpleBluetoothPrinter get instance => _instance;

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
  /// [isBLE] Whether this is a BLE device. In iOS, this is ignored cause we only support BLE for iOS.
  /// [timeout] The timeout for BLE connection. For non-BLE connection, this is ignored.
  /// Throw [BTException] if failed.
  Future<bool> connect({required String address, bool isBLE = true, Duration timeout = const Duration(seconds: 7)}) {
    return FlutterSimpleBluetoothPrinterPlatform.instance.connect(address: address, isBLE: isBLE, timeout: timeout);
  }

  /// Disconnect from a Bluetooth device.
  /// Return bool as result.
  /// Throw [BTException] when error.
  Future<bool> disconnect() {
    return FlutterSimpleBluetoothPrinterPlatform.instance.disconnect();
  }

  /// Get the connection state stream.
  Stream<BTConnectState> get connectState => FlutterSimpleBluetoothPrinterPlatform.instance.connectState;

  /// Get the current Connect State.
  Future<BTConnectState> currentConnectState() {
    return FlutterSimpleBluetoothPrinterPlatform.instance.currentConnectState();
  }

  /// Write text to the connected device.
  /// * Must connect to a device first.
  /// Return bool as result.
  /// Throw [BTException] when error.
  Future<bool> writeText(String text) {
    return FlutterSimpleBluetoothPrinterPlatform.instance.writeText(text);
  }

  /// Write raw data to the connected device.
  /// * Must connect to a device first.
  /// Return bool as result.
  /// Throw [BTException] when error.
  Future<bool> writeRawData(Uint8List bytes) {
    return FlutterSimpleBluetoothPrinterPlatform.instance.writeRawData(bytes);
  }
}
