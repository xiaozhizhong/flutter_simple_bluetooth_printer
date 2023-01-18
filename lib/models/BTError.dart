import 'package:flutter/services.dart';

/// By Xiao 2023/1/14
/// Desc:

enum BTError { bluetoothNotAvailable, permissionNotGranted, errorWithMessage, unknown }

class BTException implements Exception {
  final BTError error;
  final String? message;

  BTException(this.error, {this.message});

  factory BTException.fromPlatform(PlatformException exception) {
    BTError error;
    if (exception.code == BTError.bluetoothNotAvailable.index.toString()) {
      error = BTError.bluetoothNotAvailable;
    } else if (exception.code == BTError.permissionNotGranted.index.toString()) {
      error = BTError.permissionNotGranted;
    } else if (exception.code == BTError.errorWithMessage.index.toString()) {
      error = BTError.errorWithMessage;
    } else {
      error = BTError.unknown;
    }
    return BTException(error, message: exception.message);
  }

  @override
  String toString() {
    return 'BTException{error: $error, message: $message}';
  }
}
