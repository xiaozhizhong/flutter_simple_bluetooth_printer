import UIKit

//delayTime:延时时间。比如：.seconds(3)、.milliseconds(300)
//qosClass: 使用的全局QOS类（默认为 nil，表示主线程）
//closure: 延迟运行的代码
public func delay(by delayTime: TimeInterval, qosClass: DispatchQoS.QoSClass? = nil,
                  _ closure: @escaping () -> Void) {
    let dispatchQueue = qosClass != nil ? DispatchQueue.global(qos: qosClass!) : .main
    dispatchQueue.asyncAfter(deadline: DispatchTime.now() + delayTime, execute: closure)
}
