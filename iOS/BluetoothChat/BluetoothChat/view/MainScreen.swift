import SwiftUI

struct MainScreen: View {
    @StateObject private var bluetoothManager = BluetoothManager()

    var body: some View {
        NavigationView {
            VStack {
                HStack {
                    Button("Start Scanning") {
                        bluetoothManager.startScanning()
                    }
                    .padding()
                    .background(Color.green)
                    .foregroundColor(.white)
                    .cornerRadius(8)

                    Button("Stop Scanning") {
                        bluetoothManager.stopScanning()
                    }
                    .padding()
                    .background(Color.red)
                    .foregroundColor(.white)
                    .cornerRadius(8)
                }
                .padding()

                List(bluetoothManager.devices, id: \.identifier) { device in
                    Button(action: {
                        bluetoothManager.connect(to: device)
                    }) {
                        HStack {
                            Text("\(device.name ?? "Unknown Device") --- \(device.identifier)")
                            if bluetoothManager.connectedDevice == device {
                                Spacer()
                                Text("Connected")
                                    .foregroundColor(.green)
                                    .font(.caption)
                            }
                        }
                    }
                }
                .navigationTitle("Bluetooth Devices")
            }
        }
    }
}

