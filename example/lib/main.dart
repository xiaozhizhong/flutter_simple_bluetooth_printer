import 'dart:async';
import 'dart:io';

import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter_simple_bluetooth_printer/flutter_simple_bluetooth_printer.dart';
import 'package:flutter_simple_bluetooth_printer/models/bt_state.dart';
import 'package:flutter_simple_bluetooth_printer/models/connection_config.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatefulWidget {
  const MyApp({Key? key}) : super(key: key);

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  var bluetoothManager = FlutterSimpleBluetoothPrinter.instance;
  var _isScanning = false;
  var _isBle = true;
  var _isConnected = false;
  var devices = <BluetoothDevice>[];
  StreamSubscription<BTConnectState>? _subscriptionBtStatus;
  BTConnectState _currentStatus = BTConnectState.disconnect;

  BluetoothDevice? selectedPrinter;

  @override
  void initState() {
    super.initState();
    _discovery();

    // subscription to listen change status of bluetooth connection
    _subscriptionBtStatus = bluetoothManager.connectState.listen((status) {
      print(' ----------------- status bt $status ------------------ ');
      _currentStatus = status;
      if (status == BTConnectState.connected) {
        setState(() {
          _isConnected = true;
        });
      }
      if (status == BTConnectState.disconnect || status == BTConnectState.fail) {
        setState(() {
          _isConnected = false;
        });
      }
    });
  }

  @override
  void dispose() {
    _subscriptionBtStatus?.cancel();
    super.dispose();
  }

  void _scan() async {
    if (!await _ensureBluetoothAvailable()) return;
    devices.clear();
    try {
      setState(() {
        _isScanning = true;
      });
      if (_isBle) {
        final results = await bluetoothManager.scan(timeout: const Duration(seconds: 10));
        devices.addAll(results);
        setState(() {});
      } else {
        final bondedDevices = await bluetoothManager.getAndroidPairedDevices();
        devices.addAll(bondedDevices);
        setState(() {});
      }
    } on BTException catch (e) {
      print(e);
    } finally {
      setState(() {
        _isScanning = false;
      });
    }
  }

  void _discovery() async {
    if (!await _ensureBluetoothAvailable()) return;
    devices.clear();
    bluetoothManager.discovery().listen((device) {
      devices.add(device);
      setState(() {});
    }, onError: (e) => print(e));
  }

  Future<bool> _ensureBluetoothAvailable() async {
    final state = await bluetoothManager.getBluetoothState();
    if (state != BTState.available) {
      debugPrint("Bluetooth is not available, State: ${state.name}");
      return false;
    }
    return true;
  }

  void selectDevice(BluetoothDevice device) async {
    if (selectedPrinter != null) {
      if (device.address != selectedPrinter!.address) {
        await bluetoothManager.disconnect();
      }
    }

    selectedPrinter = device;
    setState(() {});
  }

  void _print2X1() async {
    if (!await _ensureBluetoothAvailable()) return;
    if (selectedPrinter == null) return;
    final codes =
        "^XA\r\n^MMT\r\n^PW384\r\n^LL0253\r\n^LS0\r\n^BY2,3,81^FT375,92^BCI,,N,N\r\n^FDILP-107661^FS\r\n^FT375,191^A0I,45,45^FH\\^FDILP-107661^FS\r\n^FT374,27^A0I,23,24^FH\\^FD^FS\r\n^FT372,59^A0I,23,24^FH\\^FD^FS\r\n^PQ1,0,1,Y^PQ1^XZ\r\n";

    try {
      await _connectDevice();
      if (!_isConnected) return;
      final isSuccess = await bluetoothManager.writeText(
        codes,
        // characteristicUuid: "FF00"
      );
      if (isSuccess) {
        await bluetoothManager.disconnect();
      }
    } on BTException catch (e) {
      print(e);
    }
  }

  _connectDevice() async {
    if (selectedPrinter == null) return;
    try {
      _isConnected = await bluetoothManager.connect(
        address: selectedPrinter!.address,
        config: selectedPrinter!.isLE ? const LEBluetoothConnectionConfig() : const ClassicBluetoothConnectionConfig(),
      );
    } on BTException catch (e) {
      print(e);
    }
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('Flutter Simple Bluetooth Printer example app'),
        ),
        body: Center(
          child: Container(
            height: double.infinity,
            constraints: const BoxConstraints(maxWidth: 400),
            child: SingleChildScrollView(
              padding: EdgeInsets.zero,
              child: Column(
                children: [
                  Padding(
                    padding: const EdgeInsets.all(8.0),
                    child: Row(
                      children: [
                        Expanded(
                          child: ElevatedButton(
                            onPressed: selectedPrinter == null || _isConnected
                                ? null
                                : () {
                                    _connectDevice();
                                  },
                            child: const Text("Connect", textAlign: TextAlign.center),
                          ),
                        ),
                        const SizedBox(width: 8),
                        Expanded(
                          child: ElevatedButton(
                            onPressed: selectedPrinter == null || !_isConnected
                                ? null
                                : () {
                                    if (selectedPrinter != null) {
                                      bluetoothManager.disconnect();
                                    }
                                    setState(() {
                                      _isConnected = false;
                                    });
                                  },
                            child: const Text("Disconnect", textAlign: TextAlign.center),
                          ),
                        ),
                      ],
                    ),
                  ),
                  Visibility(
                    visible: Platform.isAndroid,
                    child: SwitchListTile.adaptive(
                      contentPadding: const EdgeInsets.only(bottom: 20.0, left: 20),
                      title: const Text(
                        "BLE (low energy)",
                        textAlign: TextAlign.start,
                        style: TextStyle(fontSize: 19.0),
                      ),
                      value: _isBle,
                      onChanged: (bool? value) {
                        setState(() {
                          _isBle = value ?? false;
                          _isConnected = false;
                          selectedPrinter = null;
                          _scan();
                        });
                      },
                    ),
                  ),
                  OutlinedButton(
                    onPressed: () {
                      _scan();
                    },
                    child: const Padding(
                      padding: EdgeInsets.symmetric(vertical: 2, horizontal: 20),
                      child: Text("Rescan", textAlign: TextAlign.center),
                    ),
                  ),
                  _isScanning
                      ? const CircularProgressIndicator()
                      : Column(
                          children: devices
                              .map(
                                (device) => ListTile(
                                  title: Text(device.name),
                                  subtitle: Text(device.address),
                                  onTap: () {
                                    // do something
                                    selectDevice(device);
                                  },
                                  trailing: OutlinedButton(
                                    onPressed: selectedPrinter == null || device.name != selectedPrinter?.name
                                        ? null
                                        : () async {
                                            _print2X1();
                                          },
                                    child: const Padding(
                                      padding: EdgeInsets.symmetric(vertical: 2, horizontal: 20),
                                      child: Text("Print test", textAlign: TextAlign.center),
                                    ),
                                  ),
                                ),
                              )
                              .toList()),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
