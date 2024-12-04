//
//  MainScreen.swift
//  BluetoothChat
//
//  Created by Mate Granic on 04.12.2024..
//

import SwiftUI

struct MainScreen: View {
    @StateObject private var bluetoothManager = BluetoothManager()

        var body: some View {
            NavigationView {
                List(bluetoothManager.devices, id: \.identifier) { device in
                    Text(device.name ?? "Unknown Device")
                }
                .navigationTitle("Bluetooth Devices")
            }
        }
}

#Preview {
    MainScreen()
}
