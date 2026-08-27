import MapKit

enum LocationSearch {
    static func search(query: String, completion: @escaping (CLLocationCoordinate2D?) -> Void) {
        guard !query.trimmingCharacters(in: .whitespaces).isEmpty else {
            completion(nil)
            return
        }
        let request = MKLocalSearch.Request()
        request.naturalLanguageQuery = query
        MKLocalSearch(request: request).start { response, _ in
            completion(response?.mapItems.first?.placemark.coordinate)
        }
    }
}
