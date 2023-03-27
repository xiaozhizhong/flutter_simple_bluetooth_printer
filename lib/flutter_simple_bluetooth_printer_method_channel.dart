import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter_simple_bluetooth_printer/flutter_simple_bluetooth_printer.dart';
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

  @override
  Stream<List<BluetoothDevice>> get scanResults => _scanResults.stream;

  final StreamController<BTConnectState> _connectStateStreamController = StreamController.broadcast();

  @override
  Stream<BTConnectState> get connectState => _connectStateStreamController.stream;

  bool _connectionIsLE = false;

  @override
  bool get connectionIsLe => _connectionIsLE;

  @override
  Future<BTState> getBluetoothState() async {
    var state = await methodChannel.invokeMethod("getBluetoothState");
    return BTState.from(state);
  }

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
  /// [scanFilters] filters the scan results.
  /// Throw [BTException] if failed.
  @override
  Stream<BluetoothDevice> discovery({List<ScanFilter>? scanFilters}) async* {
    try {
      // Clear result
      _scanResults.add([]);
      Map<String, dynamic> args = {};
      args["filters"] = scanFilters == null || scanFilters.isEmpty ? null : scanFilters.map((e) => e.toMap()).toList();
      await methodChannel.invokeMethod("startDiscovery", args);
      yield* _scanResultMethodStream
          .takeUntil(_stopScanPill)
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
      }, onError: controller.addError, onDone: controller.close, cancelOnError: cancelOnError);
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
  /// [scanFilters] filters the scan results.
  /// Throw [BTException] if failed.
  @override
  Future<List<BluetoothDevice>> scan({required Duration timeout, List<ScanFilter>? scanFilters}) async {
    try {
      Future.delayed(timeout).whenComplete(() => _stopScanPill.add(null));
      await discovery(scanFilters: scanFilters).drain();
      return _scanResults.value;
    } on BTException catch (_) {
      rethrow;
    } on PlatformException catch (e) {
      throw BTException.fromPlatform(e);
    }
  }

  /// Connect to a Bluetooth device via address.
  /// Throw [BTException] if failed.
  @override
  Future<bool> connect({required String address, required ConnectionConfig config}) async {
    try {
      if (Platform.isIOS) {
        // For iOS, force using BLE
        config = const LEBluetoothConnectionConfig();
      }
      _connectionIsLE = config is LEBluetoothConnectionConfig;
      Map<String, dynamic> args = config.toMap();
      args["address"] = address;
      return await methodChannel.invokeMethod("connect", args);
    } on PlatformException catch (e) {
      throw BTException.fromPlatform(e);
    }
  }

  /// Disconnect from a Bluetooth device.
  /// Throw [BTException] if failed.
  /// [delay] is the time to wait before disconnecting.
  @override
  Future<bool> disconnect({required Duration delay}) async {
    try {
      Map<String, dynamic> args = {"isBLE": _connectionIsLE, "delay": delay.inMilliseconds};
      return await methodChannel.invokeMethod("disconnect", args).onError((error, stackTrace) => null);
    } on PlatformException catch (e) {
      throw BTException.fromPlatform(e);
    }
  }

  @override
  Future<bool> setupNotification({required String characteristicUuid}) async {
    final state = await methodChannel
        .invokeMethod("setupNotification", {"characteristicUuid": characteristicUuid}).recoverBTException();
    return state as bool? ?? false;
  }

  @override
  Future<bool> setupIndication({required String characteristicUuid}) async {
    final state = await methodChannel
        .invokeMethod("setupIndication", {"characteristicUuid": characteristicUuid}).recoverBTException();
    return state as bool? ?? false;
  }

  @override
  Future<int> requestMtu(int mtu) async {
    assert(
      mtu >= FlutterSimpleBluetoothPrinter.minAndroidMTU && mtu <= FlutterSimpleBluetoothPrinter.maxAndroidMTU,
      "MTU must be between ${FlutterSimpleBluetoothPrinter.minAndroidMTU} and ${FlutterSimpleBluetoothPrinter.maxAndroidMTU}",
    );
    final newMtu = await methodChannel.invokeMethod("requestMtu", {"mtu": mtu}).recoverBTException();
    return newMtu as int? ?? 0;
  }

  @override
  Future<bool> ensureConnected({required String address, required bool isLE}) async {
    final connected =
        await methodChannel.invokeMethod("ensureConnected", {"address": address, "isLE": isLE}).recoverBTException();
    return connected as bool? ?? false;
  }

  /// Write text to the connected device. Must connect to a device first.
  /// Throw [BTException] if failed.
  @override
  Future<bool> writeText(String text, {String? characteristicUuid}) async {
    return writeRawData(utf8.encode(text) as Uint8List, characteristicUuid: characteristicUuid);
  }

  /// Write raw data to the connected device. Must connect to a device first.
  /// Throw [BTException] if failed.
  @override
  Future<bool> writeRawData(Uint8List bytes, {String? characteristicUuid}) async {
    try {
      Map<String, dynamic> args = {"bytes": bytes, "isBLE": _connectionIsLE, "characteristicUuid": characteristicUuid};
      return await methodChannel.invokeMethod("writeData", args);
    } on PlatformException catch (e) {
      throw BTException.fromPlatform(e);
    }
  }
}

extension MethodChannelExt<T> on Future<T?> {
  Future<T?> recoverBTException() async {
    try {
      return await this;
    } on PlatformException catch (e) {
      throw BTException.fromPlatform(e);
    }
  }
}
