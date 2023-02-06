import 'dart:typed_data';

import 'package:flutter_simple_bluetooth_printer/models/connect_state.dart';
import 'package:flutter_simple_bluetooth_printer/models/printer_devices.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';
import 'flutter_simple_bluetooth_printer_method_channel.dart';

/// By Xiao 2023/1
abstract class FlutterSimpleBluetoothPrinterPlatform extends PlatformInterface {
  /// Constructs a FlutterSimpleBluetoothPrinterPlatform.
  FlutterSimpleBluetoothPrinterPlatform() : super(token: _token);

  static final Object _token = Object();

  static FlutterSimpleBluetoothPrinterPlatform _instance = MethodChannelFlutterSimpleBluetoothPrinter();

  /// The default instance of [FlutterSimpleBluetoothPrinterPlatform] to use.
  ///
  /// Defaults to [MethodChannelFlutterSimpleBluetoothPrinter].
  static FlutterSimpleBluetoothPrinterPlatform get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [FlutterSimpleBluetoothPrinterPlatform] when
  /// they register themselves.
  static set instance(FlutterSimpleBluetoothPrinterPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<List<BluetoothDevice>> getAndroidPairedDevices() {
    throw UnimplementedError('platformVersion() has not been implemented.');
  }

  Stream<List<BluetoothDevice>> get scanResults;

  Stream<BluetoothDevice> discovery() async* {
    throw UnimplementedError('discovery() has not been implemented.');
  }

  Future<bool> stopDiscovery() async {
    throw UnimplementedError('stopDiscovery() has not been implemented.');
  }

  Future<List<BluetoothDevice>> scan({required Duration timeout}) async {
    throw UnimplementedError('scan() has not been implemented.');
  }

  Future<bool> connect(
      {required String address, bool isBLE = true, Duration timeout = const Duration(seconds: 7)}) async {
    throw UnimplementedError('connect() has not been implemented.');
  }

  Future<bool> disconnect() async {
    throw UnimplementedError('disconnect() has not been implemented.');
  }

  Stream<BTConnectState> get connectState;

  Future<BTConnectState> currentConnectState() async {
    throw UnimplementedError('currentConnectState() has not been implemented.');
  }

  Future<bool> writeText(String text) async {
    throw UnimplementedError('writeText() has not been implemented.');
  }

  Future<bool> writeRawData(Uint8List bytes) async {
    throw UnimplementedError('writeRawData() has not been implemented.');
  }
}
