import Foundation

enum PairingResponseStatus: Equatable {
    case ok(message: String)
    case error(message: String)
}

enum LocationUpdateEncoder {
    static func encode(token: String, lat: Double, lng: Double) -> String {
        let payload: [String: Any] = ["token": token, "lat": lat, "lng": lng]
        let data = (try? JSONSerialization.data(withJSONObject: payload)) ?? Data()
        return String(data: data, encoding: .utf8) ?? "{}"
    }

    static func decodeResponse(_ json: String) -> PairingResponseStatus? {
        guard let data = json.data(using: .utf8),
              let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let status = obj["status"] as? String,
              let message = obj["message"] as? String
        else {
            return nil
        }
        return status == "ok" ? .ok(message: message) : .error(message: message)
    }
}
