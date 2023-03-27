class ScanFilter {
  final String? deviceName;
  final String? deviceAddress;
  final String? serviceUuid;

  ScanFilter({this.deviceName, this.deviceAddress, this.serviceUuid});

  Map<String, dynamic> toMap() {
    return {
      'deviceName': deviceName,
      'deviceAddress': deviceAddress,
      'serviceUuid': serviceUuid,
    };
  }
}
