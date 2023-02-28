/// By Xiao 2023/2
enum BTState {
  unsupported, // Bluetooth is not supported on this device
  unauthorized, // Required bluetooth permissions is not authorized on this device
  poweredOff, // Bluetooth is turned off on this device
  available; // Bluetooth is available to use

  factory BTState.from(int? value){
    switch(value){
      case 2:
        return BTState.unsupported;
      case 3:
        return BTState.unauthorized;
      case 4:
        return BTState.poweredOff;
      case 5:
        return BTState.available;
      default:
        return BTState.unsupported;
    }
  }
}
