import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter_simple_bluetooth_printer/models/BTError.dart';
import 'package:flutter_simple_bluetooth_printer/models/connect_state.dart';
import 'package:flutter_simple_bluetooth_printer/models/printer_devices.dart';
import 'package:rxdart/rxdart.dart';
import 'flutter_simple_bluetooth_printer_platform_interface.dart';

/// By Xiao 2023/1
/// An implementation of [FlutterSimpleBluetoothPrinterPlatform] that uses method channels.
class MethodChannelFlutterSimpleBluetoothPrinter extends FlutterSimpleBluetoothPrinterPlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('flutter_simple_bluetooth_printer/method');

  @visibleForTesting
  final eventChannel = const EventChannel('flutter_simple_bluetooth_printer/event');

  MethodChannelFlutterSimpleBluetoothPrinter() {
    methodChannel.setMethodCallHandler((call) {
      _methodStreamController.add(call);
      return Future(() => null);
    });
    eventChannel.receiveBroadcastStream().listen((event) {
      if (event is Map && event.containsKey("state")) {
        var state = BTConnectState.values[event["state"]];
        _connectStateStreamController.add(state);
      }
    });
  }

  final StreamController<MethodCall> _methodStreamController = StreamController.broadcast();

  Stream<MethodCall> get _methodStream => _methodStreamController.stream;

  Stream<MethodCall> get _scanResultMethodStream => _methodStream.where((event) => event.method == "scanResult");
  final PublishSubject _stopScanPill = PublishSubject();

  final BehaviorSubject<List<BluetoothDevice>> _scanResults = BehaviorSubject.seeded([]);

  Stream<List<BluetoothDevice>> get scanResults => _scanResults.stream;

  final StreamController<BTConnectState> _connectStateStreamController = StreamController.broadcast();

  @override
  Stream<BTConnectState> get connectState => _connectStateStreamController.stream;

  bool _isBLE = false;

  /// Get paired devices for Android.
  /// For iOS, it will return an empty list.
  /// Throw [BTException] if failed.
  @override
  Future<List<BluetoothDevice>> getAndroidPairedDevices() async {
    if (!Platform.isAndroid) return [];
    try {
      final List list = await methodChannel.invokeMethod('getBondedDevices');
      return list.map((e) => BluetoothDevice.fromMap(e)).toList();
    } on PlatformException catch (e) {
      throw BTException.fromPlatform(e);
    }
  }

  /// Starts scan for Bluetooth LE devices
  /// Note: This is a continuous behavior, don't forget to call [stopDiscovery].
  /// Throw [BTException] if failed.
  @override
  Stream<BluetoothDevice> discovery() async* {
    try {
      // Clear result
      _scanResults.add([]);
      await methodChannel.invokeMethod("startDiscovery");
      yield* _scanResultMethodStream.takeUntil(_stopScanPill)
          .doOnDone(stopDiscovery)
          .map((event) => event.arguments)
          .transform(StreamTransformer(_addDeviceTransform));
    } on BTException catch (_) {
      rethrow;
    } on PlatformException catch (e) {
      throw BTException.fromPlatform(e);
    }
  }


  StreamSubscription<BluetoothDevice> _addDeviceTransform(Stream<dynamic> input, bool cancelOnError) {
    var controller = StreamController<BluetoothDevice>(sync: true);
    controller.onListen = () {
      var subscription = input.listen((data) {
        var device = BluetoothDevice.fromMap(data);
        if (_tryAddDevice(device)) {
          controller.add(device);
        }
      },
          onError: controller.addError,
          onDone: controller.close,
          cancelOnError: cancelOnError);
      controller
        ..onPause = subscription.pause
        ..onResume = subscription.resume
        ..onCancel = subscription.cancel;
    };
    return controller.stream.listen(null);
  }

  bool _tryAddDevice(BluetoothDevice device) {
    final list = _scanResults.valueOrNull ?? [];
    if (!list.any((element) => element.address == device.address)) {
      list.add(device);
      _scanResults.add(list);
      return true;
    }
    return false;
  }

  /// Stops scan for Bluetooth LE devices.
  /// Throw [BTException] if failed.
  @override
  Future<bool> stopDiscovery() async {
    try {
      await methodChannel.invokeMethod("stopDiscovery");
      return true;
    } on PlatformException catch (e) {
      throw BTException.fromPlatform(e);
    }
  }

  /// Scan for Bluetooth LE devices until [timeout] is reached.
  /// Throw [BTException] if failed.
  @override
  Future<List<BluetoothDevice>> scan({required Duration timeout}) async {
    try {
      Future.delayed(timeout).whenComplete(() => _stopScanPill.add(null));
      await discovery().drain();
      return _scanResults.value;
    } on BTException catch (_) {
      rethrow;
    } on PlatformException catch (e) {
      throw BTException.fromPlatform(e);
    }
  }

  /// Connect to a Bluetooth device via address.
  /// [isBLE] Whether this is a BLE device. In iOS, this is ignored cause we only support BLE for iOS.
  /// [timeout] The timeout for BLE connection. For non-BLE connection, this is ignored.
  /// Throw [BTException] if failed.
  @override
  Future<bool> connect(
      {required String address, bool isBLE = true, Duration timeout = const Duration(seconds: 7)}) async {
    try {
      if (Platform.isIOS) {
        _isBLE = true;
      } else {
        _isBLE = isBLE;
      }
      Map<String, dynamic> args = {"address": address, "isBLE": _isBLE, "timeout": timeout.inMilliseconds};
      return await methodChannel.invokeMethod("connect", args);
    } on PlatformException catch (e) {
      throw BTException.fromPlatform(e);
    }
  }

  /// Disconnect from a Bluetooth device.
  /// Throw [BTException] if failed.
  @override
  Future<bool> disconnect() async {
    try {
      Map<String, dynamic> args = {"isBLE": _isBLE};
      return await methodChannel.invokeMethod("disconnect", args);
    } on PlatformException catch (e) {
      throw BTException.fromPlatform(e);
    }
  }

  /// Get the current Connect State.
  @override
  Future<BTConnectState> currentConnectState() async {
    if (await connectState.isEmpty) {
      return Future.value(BTConnectState.disconnect);
    }
    return connectState.last;
  }

  /// Write text to the connected device. Must connect to a device first.
  /// Throw [BTException] if failed.
  @override
  Future<bool> writeText(String text) async {
    return writeRawData(utf8.encode(text) as Uint8List);
  }

  /// Write raw data to the connected device. Must connect to a device first.
  /// Throw [BTException] if failed.
  @override
  Future<bool> writeRawData(Uint8List bytes) async {
    try {
      Map<String, dynamic> args = {"bytes": bytes, "isBLE": _isBLE};
      return await methodChannel.invokeMethod("writeData", args);
    } on PlatformException catch (e) {
      throw BTException.fromPlatform(e);
    }
  }
}
