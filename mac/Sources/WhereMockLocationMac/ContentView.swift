import SwiftUI
import MapKit
import AppKit

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

    @State private var connection = PairingConnection()

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                TextField("주소 검색", text: $searchText)
                Button("검색") { search() }
            }

            MapReader { proxy in
                Map(position: $cameraPosition) {
                    Marker("목표 위치", coordinate: pin)
                }
                .onTapGesture { screenPoint in
                    if let coordinate = proxy.convert(screenPoint, from: .local) {
                        pin = coordinate
                        sendUpdate()
                    }
                }
                .mapControls {
                    MapZoomStepper()
                }
            }
            .frame(width: 360, height: 260)

            GroupBox("페어링") {
                VStack(alignment: .leading, spacing: 4) {
                    TextField("폰 IP", text: $ip)
                    TextField("포트", text: $port)
                    TextField("토큰", text: $token)
                }
            }

            Text("위도: \(pin.latitude, specifier: "%.5f")  경도: \(pin.longitude, specifier: "%.5f")")
                .font(.caption)
                .foregroundStyle(.secondary)

            if !statusMessage.isEmpty {
                Text(statusMessage)
                    .font(.caption)
            }

            Button("종료") {
                NSApplication.shared.terminate(nil)
            }
        }
        .padding()
        .frame(width: 400)
    }

    private func search() {
        LocationSearch.search(query: searchText) { coordinate in
            DispatchQueue.main.async {
                guard let coordinate else {
                    statusMessage = "\"\(searchText)\" 검색 결과 없음"
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
            statusMessage = "IP, 포트, 토큰부터 입력해줘"
            return
        }
        statusMessage = "전송 중…"
        connection.send(target: target, token: token, lat: pin.latitude, lng: pin.longitude) { result in
            DispatchQueue.main.async {
                switch result {
                case .success(.ok(let message)):
                    statusMessage = "전송됨: \(message)"
                case .success(.error(let message)):
                    statusMessage = "거부됨: \(message)"
                case .failure(let error):
                    statusMessage = "실패: \(error.localizedDescription)"
                }
            }
        }
    }
}
