import Foundation

struct PairingTarget: Equatable {
    var ip: String
    var port: String
    var token: String

    var isComplete: Bool {
        !ip.trimmingCharacters(in: .whitespaces).isEmpty
            && Int(port) != nil
            && !token.trimmingCharacters(in: .whitespaces).isEmpty
    }

    var url: URL? {
        guard isComplete else { return nil }
        return URL(string: "ws://\(ip):\(port)")
    }
}
