import SwiftUI
import UIKit

/// A page viewer that uses iOS's native UIPageViewController with `.pageCurl`
/// transition style — the same page-turn engine used by Apple Books.
/// Provides real corner bending, paper simulation, and interactive drag.
struct PageCurlView<Content: View>: UIViewControllerRepresentable {
    @Binding var currentPage: Int
    let totalPages: Int
    let content: (Int) -> Content

    init(currentPage: Binding<Int>, totalPages: Int, @ViewBuilder content: @escaping (Int) -> Content) {
        self._currentPage = currentPage
        self.totalPages = totalPages
        self.content = content
    }

    func makeUIViewController(context: Context) -> UIPageViewController {
        let pvc = UIPageViewController(
            transitionStyle: .pageCurl,
            navigationOrientation: .horizontal,
            options: [
                .spineLocation: NSNumber(value: UIPageViewController.SpineLocation.min.rawValue)
            ]
        )
        pvc.dataSource = context.coordinator
        pvc.delegate = context.coordinator
        pvc.isDoubleSided = false
        pvc.view.backgroundColor = .systemBackground

        // Set initial page
        let initial = context.coordinator.makeHostingController(for: currentPage)
        pvc.setViewControllers([initial], direction: .forward, animated: false)

        return pvc
    }

    func updateUIViewController(_ pvc: UIPageViewController, context: Context) {
        let coord = context.coordinator
        // Keep coordinator's parent in sync so content closure stays current
        coord.parent = self

        guard currentPage != coord.displayedPage,
              currentPage >= 1, currentPage <= totalPages else { return }

        let direction: UIPageViewController.NavigationDirection =
            currentPage > coord.displayedPage ? .forward : .reverse
        let vc = coord.makeHostingController(for: currentPage)
        coord.displayedPage = currentPage

        pvc.setViewControllers([vc], direction: direction, animated: true)
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }

    // MARK: - Coordinator

    class Coordinator: NSObject, UIPageViewControllerDataSource, UIPageViewControllerDelegate {
        var parent: PageCurlView
        var displayedPage: Int

        init(_ parent: PageCurlView) {
            self.parent = parent
            self.displayedPage = parent.currentPage
        }

        func makeHostingController(for page: Int) -> UIViewController {
            let swiftUIView = parent.content(page)
            let hc = UIHostingController(rootView: AnyView(swiftUIView))
            hc.view.tag = page
            hc.view.backgroundColor = .systemBackground
            return hc
        }

        // MARK: DataSource

        func pageViewController(
            _ pageViewController: UIPageViewController,
            viewControllerBefore viewController: UIViewController
        ) -> UIViewController? {
            let page = viewController.view.tag
            guard page > 1 else { return nil }
            return makeHostingController(for: page - 1)
        }

        func pageViewController(
            _ pageViewController: UIPageViewController,
            viewControllerAfter viewController: UIViewController
        ) -> UIViewController? {
            let page = viewController.view.tag
            guard page < parent.totalPages else { return nil }
            return makeHostingController(for: page + 1)
        }

        // MARK: Delegate — fires when a gesture-driven page turn completes

        func pageViewController(
            _ pageViewController: UIPageViewController,
            didFinishAnimating finished: Bool,
            previousViewControllers: [UIViewController],
            transitionCompleted completed: Bool
        ) {
            guard completed,
                  let current = pageViewController.viewControllers?.first else { return }
            let page = current.view.tag
            displayedPage = page
            parent.currentPage = page
        }
    }
}
