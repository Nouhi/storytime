import Foundation
import UIKit

class EPUBManager {
    static let shared = EPUBManager()

    private var documentsDir: URL {
        FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
    }

    func saveEPUB(data: Data, filename: String) -> URL {
        let fileURL = documentsDir.appendingPathComponent(filename)
        try? data.write(to: fileURL)
        return fileURL
    }

    func openInBooks(fileURL: URL, from viewController: UIViewController? = nil) {
        let activityVC = UIActivityViewController(
            activityItems: [fileURL],
            applicationActivities: nil
        )

        guard let scene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
              let rootVC = scene.windows.first?.rootViewController else {
            return
        }

        let presenter = viewController ?? rootVC
        // For iPad
        activityVC.popoverPresentationController?.sourceView = presenter.view
        activityVC.popoverPresentationController?.sourceRect = CGRect(
            x: presenter.view.bounds.midX,
            y: presenter.view.bounds.midY,
            width: 0,
            height: 0
        )

        presenter.present(activityVC, animated: true)
    }

    func localEPUBExists(filename: String) -> Bool {
        let fileURL = documentsDir.appendingPathComponent(filename)
        return FileManager.default.fileExists(atPath: fileURL.path)
    }

    func getLocalEPUBURL(filename: String) -> URL {
        documentsDir.appendingPathComponent(filename)
    }
}
