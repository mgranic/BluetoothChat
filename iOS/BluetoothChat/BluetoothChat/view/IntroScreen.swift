//
//  ContentView.swift
//  BluetoothChat
//
//  Created by Mate Granic on 04.12.2024..
//

import SwiftUI

struct IntroScreen: View {
    var body: some View {
        NavigationStack {
            VStack {
                HStack {
                    NavigationLink(destination: MainScreen()) {
                        Text("Start server")
                    }
                    Spacer()
                    NavigationLink(destination: MainScreen()) {
                        Text("Start client")
                    }
                }
            }
            .padding()
        }
    }
}
