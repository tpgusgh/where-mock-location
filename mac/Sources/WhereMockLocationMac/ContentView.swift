import SwiftUI
import MapKit

struct ContentView: View {
    @State private var cameraPosition = MapCameraPosition.region(
        MKCoordinateRegion(
            center: CLLocationCoordinate2D(latitude: 37.5665, longitude: 126.9780),
            span: MKCoordinateSpan(latitudeDelta: 0.05, longitudeDelta: 0.05)
        )
    )
    @State private var pin = CLLocationCoordinate2D(latitude: 37.5665, longitude: 126.9780)
    @State private var searchText = ""
    @State private var ip = ""
    @State private var port = "8765"
    @State private var token = ""
    @State private var statusMessage = ""

    private let connection = PairingConnection()

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                TextField("Search address", text: $searchText)
                Button("Search") { search() }
            }

            MapReader { proxy in
                Map(position: $cameraPosition) {
                    Marker("Target", coordinate: pin)
                }
                .onTapGesture { screenPoint in
                    if let coordinate = proxy.convert(screenPoint, from: .local) {
                        pin = coordinate
                        sendUpdate()
                    }
                }
            }
            .frame(width: 360, height: 260)

            GroupBox("Pairing") {
                VStack(alignment: .leading, spacing: 4) {
                    TextField("Phone IP", text: $ip)
                    TextField("Port", text: $port)
                    TextField("Token", text: $token)
                }
            }

            Text("Lat: \(pin.latitude, specifier: "%.5f")  Lng: \(pin.longitude, specifier: "%.5f")")
                .font(.caption)
                .foregroundStyle(.secondary)

            if !statusMessage.isEmpty {
                Text(statusMessage)
                    .font(.caption)
            }
        }
        .padding()
        .frame(width: 400)
    }

    private func search() {
        LocationSearch.search(query: searchText) { coordinate in
            DispatchQueue.main.async {
                guard let coordinate else {
                    statusMessage = "No results for \"\(searchText)\""
                    return
                }
                pin = coordinate
                cameraPosition = .region(
                    MKCoordinateRegion(
                        center: coordinate,
                        span: MKCoordinateSpan(latitudeDelta: 0.05, longitudeDelta: 0.05)
                    )
                )
                sendUpdate()
            }
        }
    }

    private func sendUpdate() {
        let target = PairingTarget(ip: ip, port: port, token: token)
        guard target.isComplete else {
            statusMessage = "Enter IP, port, and token first"
            return
        }
        connection.send(target: target, token: token, lat: pin.latitude, lng: pin.longitude) { result in
            DispatchQueue.main.async {
                switch result {
                case .success(.ok(let message)):
                    statusMessage = "Sent: \(message)"
                case .success(.error(let message)):
                    statusMessage = "Rejected: \(message)"
                case .failure(let error):
                    statusMessage = "Failed: \(error.localizedDescription)"
                }
            }
        }
    }
}
