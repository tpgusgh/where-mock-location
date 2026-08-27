import Foundation

enum PairingConnectionError: Error {
    case invalidTarget
    case malformedResponse
}

final class PairingConnection {
    private let session: URLSession = {
        let configuration = URLSessionConfiguration.default
        configuration.timeoutIntervalForRequest = 5
        return URLSession(configuration: configuration)
    }()

    func send(
        target: PairingTarget,
        token: String,
        lat: Double,
        lng: Double,
        completion: @escaping (Result<PairingResponseStatus, Error>) -> Void
    ) {
        guard let url = target.url else {
            completion(.failure(PairingConnectionError.invalidTarget))
            return
        }

        let task = session.webSocketTask(with: url)
        task.resume()

        let finish: (Result<PairingResponseStatus, Error>) -> Void = { result in
            task.cancel(with: .normalClosure, reason: nil)
            completion(result)
        }

        let message = URLSessionWebSocketTask.Message.string(
            LocationUpdateEncoder.encode(token: token, lat: lat, lng: lng)
        )
        task.send(message) { error in
            if let error = error {
                finish(.failure(error))
                return
            }
            task.receive { result in
                switch result {
                case .success(let message):
                    switch message {
                    case .string(let text):
                        if let status = LocationUpdateEncoder.decodeResponse(text) {
                            finish(.success(status))
                        } else {
                            finish(.failure(PairingConnectionError.malformedResponse))
                        }
                    case .data:
                        finish(.failure(PairingConnectionError.malformedResponse))
                    @unknown default:
                        finish(.failure(PairingConnectionError.malformedResponse))
                    }
                case .failure(let error):
                    finish(.failure(error))
                }
            }
        }
    }
}
