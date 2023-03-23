abstract class ConnectionConfig {
  const ConnectionConfig();

  Map<String, dynamic> toMap();
}

/// Only for Android.
class ClassicBluetoothConnectionConfig extends ConnectionConfig {
  const ClassicBluetoothConnectionConfig();

  @override
  Map<String, dynamic> toMap() {
    return {
      "isBLE": false,
    };
  }
}

class LEBluetoothConnectionConfig extends ConnectionConfig {
  /// Android Only. Default to false.
  final bool androidAutoConnect;

  /// The timeout for BLE connection.
  /// If null, will use the default timeout of platform.
  /// Use it carefully on Android cause it may leave Android's BLE stack in an inconsistent state.
  /// Only for Android currently.
  final Duration? timeout;

  /// Connection Priority.
  /// Only for Android currently.
  final LEConnectionPriority connectionPriority;

  const LEBluetoothConnectionConfig(
      {this.androidAutoConnect = false,
      this.timeout,
      this.connectionPriority = LEConnectionPriority.balanced});

  @override
  Map<String, dynamic> toMap() {
    return {
      "isBLE": true,
      "androidAutoConnect": androidAutoConnect,
      "timeout": timeout == null ? -1 : timeout!.inMilliseconds,
      "connectionPriority": connectionPriority.index,
    };
  }
}

enum LEConnectionPriority {
  /// Default connection priority.
  /// For Android, it's [BluetoothGatt.CONNECTION_PRIORITY_BALANCED].
  /// For iOS, it's [CBPeripheralManagerConnectionLatency.normal].
  balanced,

  /// High connection priority.
  /// For Android, it's [BluetoothGatt.CONNECTION_PRIORITY_HIGH].
  /// For iOS, it's [CBPeripheralManagerConnectionLatency.low].
  high,

  /// Low connection priority.
  /// For Android, it's [BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER].
  /// For iOS, it's [CBPeripheralManagerConnectionLatency.high].
  low,
}
