import Flutter
import UIKit
import CoreBluetooth

//
//  SwiftFlutterSimpleBluetoothPrinterPlugin
//
//  Created by xiao on 2023/1.
//

let PluginNameSpace = "flutter_simple_bluetooth_printer"

public class SwiftFlutterSimpleBluetoothPrinterPlugin: NSObject, FlutterPlugin,FlutterStreamHandler, BluetoothDelegate {

    public static func register(with registrar: FlutterPluginRegistrar) {
        let instance = SwiftFlutterSimpleBluetoothPrinterPlugin()

        instance._channel = FlutterMethodChannel(name: "\(PluginNameSpace)/method", binaryMessenger: registrar.messenger())
        registrar.addMethodCallDelegate(instance, channel: instance._channel!)

        let event = FlutterEventChannel(name: "\(PluginNameSpace)/event", binaryMessenger: registrar.messenger())
        event.setStreamHandler(instance)

        instance.bluetoothManager.delegate = instance
    }

    let bluetoothManager = BluetoothManager.getInstance()
    var _channel: FlutterMethodChannel? = nil;
    var _eventSink: FlutterEventSink? = nil
    var nearbyPeripheralInfos: [PeripheralInfos] = []
    var _connectResult: FlutterResult? = nil
    var _writeResult: FlutterResult? = nil
    var _pendingWriteData: FlutterStandardTypedData? = nil

    public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        switch call.method {
        case "startDiscovery":
            _ensureBluetoothAvailable(result){
                _discovery(result)
            }
        case "stopDiscovery":
            _ensureBluetoothAvailable(result){
                bluetoothManager.stopScanPeripheral()
                result(true)
            }
        case "connect":
            _ensureBluetoothAvailable(result){
                _connectToPeripheral(call.arguments as! NSDictionary, result)
            }
        case "disconnect":
            _ensureBluetoothAvailable(result){
                bluetoothManager.disconnectPeripheral()
                result(true)
            }
        case "writeData":
            _ensureBluetoothAvailable(result){
                _writeData(call.arguments as! NSDictionary, result)
            }
        default:
            result(FlutterMethodNotImplemented)
        }
    }
    
    private func _ensureBluetoothAvailable(_ result: FlutterResult,_ onNext:()->Void){
        if (bluetoothManager.state == .unauthorized) {
            // No permission
            result(BTError.PermissionNotGranted.toError())
            return
        }
        if(bluetoothManager.state != .poweredOn){
            // Not available
            result(BTError.BluetoothNotAvailable.toError())
            return
        }
        onNext()
    }

    private func _discovery(_ result: FlutterResult) {
        // Results will callback in didDiscoverPeripheral
        result(true)
        nearbyPeripheralInfos.removeAll()
        bluetoothManager.startScanPeripheral()
    }

    private func _connectToPeripheral(_ arguments: NSDictionary, _ result: @escaping FlutterResult) {
        let uuId = arguments["address"] as! String?
        if(uuId == nil || uuId!.isEmpty){
            // Address is empty
            result(false);
            return
        }
//        if (bluetoothManager.connectedPeripheral != nil && bluetoothManager.connectedPeripheral?.identifier.uuidString == uuId) {
//            // Already connected
//            result(true)
//            return
//        }
        var peripheral = nearbyPeripheralInfos.first(where: { $0.peripheral.identifier.uuidString == uuId })?.peripheral
        if(peripheral == nil){
            let identifiers = [UUID(uuidString: uuId!)!]
            let peripheralResults = bluetoothManager._manager?.retrievePeripherals(withIdentifiers: identifiers)
            if (peripheralResults == nil || ((peripheralResults?.isEmpty) != nil)) {
                // peripheral not found  by identifier
                result(false)
                return
            }
            peripheral = peripheralResults!.first
        }
        _updateConnectState(.CONNECTING)
        _connectResult = result
        // Result will be callback in didConnectedPeripheral and failToConnectPeripheral
        bluetoothManager.connectPeripheral(peripheral!)
    }
    
    

    private func _writeData(_ arguments: NSDictionary, _ result: @escaping FlutterResult) {
        guard let peripheral = bluetoothManager.connectedPeripheral else {
            // Not connected to peripheral
            result(false)
            return
        }
        _pendingWriteData = arguments["bytes"] as? FlutterStandardTypedData
        _writeResult = result
        // Discover services and then discover characteristics. After all discovered, will do write in _writeWithCharacteristics
        // Result will callback in didDiscoverServices & didDiscoverCharacteritics & didFailToDiscoverCharacteritics
        peripheral.discoverServices(nil)
    }
    
    private func _writeWithCharacteristics(){
        if _pendingWriteData == nil {
            return
        }
        guard let peripheral = bluetoothManager.connectedPeripheral else {
            // Not connected to peripheral
            _sendbackWriteResult(false)
            return
        }
        var toWriteCharacteristics: [CBCharacteristic]? = nil
        for service in peripheral.services! {
            guard let characteristics = service.characteristics else {
                continue
            }
            let writableCharacteristics = characteristics.filter{(element)-> Bool in element.properties.names.contains("Write")}
                .sorted{(s1,s2)-> Bool in
                    let uuid1 = s1.uuid.UUIDValue!.integers
                    let uuid2 = s2.uuid.UUIDValue!.integers
                    if uuid1.0 == uuid2.0 {
                        return abs(uuid1.0) > abs(uuid2.1)
                    }
                    return abs(uuid1.0) > abs(uuid2.0)
                }
            if(!writableCharacteristics.isEmpty){
                toWriteCharacteristics = writableCharacteristics
            }
        }
        if toWriteCharacteristics == nil || toWriteCharacteristics!.isEmpty{
            // No CBCharacteristic to handle write
            _sendbackWriteResult(false)
            return
        }
        bluetoothManager.writeValue(data: _pendingWriteData!.data, forCharacteristic: toWriteCharacteristics!.first!, type: .withResponse)
        _pendingWriteData = nil
    }
    
    private func _sendbackWriteResult(_ data:Any){
        if _writeResult == nil  {
            return
        }
        _writeResult!(data)
        _writeResult = nil
    }
    
    public func didDiscoverPeripheral(_ peripheral: CoreBluetooth.CBPeripheral, advertisementData: [String: Any], RSSI: NSNumber) {
        if peripheral.name == nil || peripheral.name!.isEmpty {
            return
        }

        let peripheralInfo = PeripheralInfos(peripheral,RSSI.intValue)
        if !nearbyPeripheralInfos.contains(peripheralInfo) {
            peripheralInfo.advertisementData = advertisementData
            nearbyPeripheralInfos.append(peripheralInfo)
        } else {
            guard let index = nearbyPeripheralInfos.firstIndex(of: peripheralInfo) else {
                return
            }

            let originPeripheralInfo = nearbyPeripheralInfos[index]
            let nowTimeInterval = Date().timeIntervalSince1970

            // If the last update within one second, then ignore it
            guard nowTimeInterval - originPeripheralInfo.lastUpdatedTimeInterval >= 1.0 else {
                return
            }

            originPeripheralInfo.lastUpdatedTimeInterval = nowTimeInterval
            originPeripheralInfo.advertisementData = advertisementData
            return
        }
        print("find peripheral -> \(peripheral.name!)")

        let device = BluetoothDevice(peripheralInfo).toMap()
        _sendMessageToFlutter(method: "scanResult", argument: device)
    }

    public func didConnectedPeripheral(_ connectedPeripheral: CoreBluetooth.CBPeripheral) {
        if _connectResult != nil {
            _connectResult!(true)
            _connectResult = nil
        }
        _updateConnectState(.CONNECTED)
    }

    public func failToConnectPeripheral(_ peripheral: CoreBluetooth.CBPeripheral, error: Error) {
        if _connectResult != nil {
            _connectResult!(false)
            _connectResult = nil
        }
        _updateConnectState(.FAIL)
    }
    
    var _serviceCount = 0
    public func didDiscoverServices(_ peripheral: CBPeripheral) {
        print("MainController --> didDiscoverService:\(peripheral.services?.description ?? "Unknow Service")")
//
//        let tmp = PeripheralInfos(peripheral)
//        guard let index = nearbyPeripheralInfos.firstIndex(of: tmp) else {
//            return
//        }
//        nearbyPeripheralInfos[index] = nearbyPeripheralInfos[index].copyWith(peripheral)
        if peripheral.services == nil || peripheral.services!.isEmpty {
            // No Services to handle write
            _sendbackWriteResult(false)
            return
        }
        _serviceCount = peripheral.services!.count
        bluetoothManager.discoverCharacteristics()
    }
    
    public func didDiscoverCharacteritics(_ service: CBService){
        _serviceCount-=1
        if _serviceCount == 0 && _writeResult != nil{
            _writeWithCharacteristics()
        }
    }
    
    public func didFailToDiscoverCharacteritics(_ error: Error){
        _sendbackWriteResult(false)
    }
    
    public func didWriteValueForCharacteristic(_ peripheral: CBPeripheral, didWriteValueFor characteristic: CBCharacteristic){
        _sendbackWriteResult(true)
    }
    
    public func didFailToWriteValueForCharacteristic(_ peripheral: CBPeripheral, didWriteValueFor characteristic: CBCharacteristic, error: Error){
        _sendbackWriteResult(false)
    }
    
    public func didDisconnectPeripheral(_ peripheral: CoreBluetooth.CBPeripheral) {
        _updateConnectState(.DISCONNECT)
    }

    private func _updateConnectState(_ state:ConnectState){
        let stateEvent = ["state":state.rawValue]
        _eventSink?(stateEvent)
    }

    private func _sendMessageToFlutter(method: String, argument: NSObject) {
        _channel?.invokeMethod(method, arguments:argument)
    }

    public func onListen(withArguments arguments: Any?, eventSink events: @escaping FlutterEventSink) -> FlutterError? {
        _eventSink = events
        return nil
    }

    public func onCancel(withArguments arguments: Any?) -> FlutterError? {
        _eventSink = nil
        return nil
    }
}

 class PeripheralInfos: Equatable, Hashable {
    let peripheral: CBPeripheral
     let rssi:Int
    var advertisementData: [String: Any] = [:]
    var lastUpdatedTimeInterval: TimeInterval

     init(_ peripheral: CBPeripheral,_ rssi:Int = -1) {
        self.peripheral = peripheral
         self.rssi = rssi
        lastUpdatedTimeInterval = Date().timeIntervalSince1970
    }

    static func ==(lhs: PeripheralInfos, rhs: PeripheralInfos) -> Bool {
        lhs.peripheral.isEqual(rhs.peripheral)
    }
     
     func copyWith(_ peripheral: CBPeripheral) ->PeripheralInfos{
         let p = PeripheralInfos(peripheral,self.rssi)
         p.advertisementData = self.advertisementData
         p.lastUpdatedTimeInterval = self.lastUpdatedTimeInterval
         return p
     }
     
     func hash(into hasher: inout Hasher) {
         hasher.combine(peripheral)
     }

}

 class BluetoothDevice{
    let name:String
    let address:String
    let rssi:Int
    let advertisementData: [String: Any]

    init(_ info: PeripheralInfos){
        self.name = info.peripheral.name!
        self.address = info.peripheral.identifier.uuidString
        self.rssi = info.rssi
        self.advertisementData = info.advertisementData
    }
    
    func toMap() -> NSDictionary {
        var dictionary : [String:String] = [:]
        self.advertisementData.forEach{ key,value in
            dictionary[CBAdvertisementData.getAdvertisementDataName(key)] = CBAdvertisementData.getAdvertisementDataStringValue(self.advertisementData, key: key)
        }
        return [
            "name":self.name,
            "address":self.address,
            "rssi":self.rssi,
            "advertisementData":dictionary
        ]
    }
}

enum BTError:Int{
    case BluetoothNotAvailable,PermissionNotGranted,ErrorWithMessage,Unknown
    
    func toError(_ message:String? = nil) -> FlutterError{
        return FlutterError(code:String(self.rawValue),message:message,details:nil)
    }
}

enum ConnectState:Int{
    case CONNECTING, CONNECTED, FAIL, DISCONNECT
}

extension Dictionary where Key: ExpressibleByStringLiteral, Value:AnyObject {
    
    var jsonString:String {
        do {
            let stringData = try JSONSerialization.data(withJSONObject: self as NSDictionary, options: JSONSerialization.WritingOptions.prettyPrinted)
            if let string = String(data: stringData, encoding: String.Encoding.utf8){
                return string
            }
        } catch _ {
            
        }
        return ""
    }
}
