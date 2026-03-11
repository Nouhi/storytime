import SwiftUI

struct StarfieldView: View {
    private struct Star {
        let x: CGFloat
        let y: CGFloat
        let size: CGFloat
        let baseBrightness: CGFloat
        let twinkleSpeed: Double
        let twinkleOffset: Double
    }

    private struct ShootingStar {
        let startX: CGFloat
        let startY: CGFloat
        let angle: Double
        let speed: CGFloat
        let startTime: Double
        let duration: Double
    }

    @State private var stars: [Star] = []
    @State private var shootingStar: ShootingStar?
    @State private var nextShootingStarTime: Double = 0

    private let starCount = 100

    var body: some View {
        TimelineView(.animation) { timeline in
            Canvas { context, size in
                let time = timeline.date.timeIntervalSinceReferenceDate

                // Generate stars on first render
                if stars.isEmpty {
                    generateStars(in: size)
                }

                // Draw twinkling stars
                for star in stars {
                    let brightness = star.baseBrightness * (0.5 + 0.5 * CGFloat(sin(time * star.twinkleSpeed + star.twinkleOffset)))
                    let rect = CGRect(
                        x: star.x * size.width - star.size / 2,
                        y: star.y * size.height - star.size / 2,
                        width: star.size,
                        height: star.size
                    )
                    context.fill(
                        Path(ellipseIn: rect),
                        with: .color(.white.opacity(Double(brightness)))
                    )
                }

                // Handle shooting star
                if let shooting = shootingStar {
                    let elapsed = time - shooting.startTime
                    if elapsed >= 0 && elapsed < shooting.duration {
                        let progress = CGFloat(elapsed / shooting.duration)
                        let tailLength: CGFloat = 40

                        let headX = shooting.startX + cos(shooting.angle) * shooting.speed * progress * size.width
                        let headY = shooting.startY + sin(shooting.angle) * shooting.speed * progress * size.height
                        let tailX = headX - cos(shooting.angle) * tailLength
                        let tailY = headY - sin(shooting.angle) * tailLength

                        let fadeIn = min(progress * 5, 1.0)
                        let fadeOut = max(0, 1.0 - (progress - 0.7) / 0.3)
                        let alpha = Double(min(fadeIn, fadeOut) * 0.8)

                        var path = Path()
                        path.move(to: CGPoint(x: tailX, y: tailY))
                        path.addLine(to: CGPoint(x: headX, y: headY))

                        context.stroke(
                            path,
                            with: .color(.white.opacity(alpha)),
                            lineWidth: 1.5
                        )

                        // Bright head dot
                        let headRect = CGRect(x: headX - 1.5, y: headY - 1.5, width: 3, height: 3)
                        context.fill(
                            Path(ellipseIn: headRect),
                            with: .color(.white.opacity(alpha))
                        )
                    }
                }

                // Spawn new shooting star periodically
                if time > nextShootingStarTime {
                    spawnShootingStar(at: time, in: size)
                }
            }
        }
        .onAppear {
            nextShootingStarTime = Date.timeIntervalSinceReferenceDate + Double.random(in: 5...10)
        }
        .allowsHitTesting(false)
    }

    private func generateStars(in size: CGSize) {
        var newStars: [Star] = []
        for _ in 0..<starCount {
            newStars.append(Star(
                x: CGFloat.random(in: 0...1),
                y: CGFloat.random(in: 0...1),
                size: CGFloat.random(in: 1...3),
                baseBrightness: CGFloat.random(in: 0.3...0.9),
                twinkleSpeed: Double.random(in: 0.5...3.0),
                twinkleOffset: Double.random(in: 0...(2 * .pi))
            ))
        }
        DispatchQueue.main.async {
            stars = newStars
        }
    }

    private func spawnShootingStar(at time: Double, in size: CGSize) {
        let shooting = ShootingStar(
            startX: CGFloat.random(in: 0.1...0.9) * size.width,
            startY: CGFloat.random(in: 0.05...0.4) * size.height,
            angle: Double.random(in: 0.3...0.8),
            speed: CGFloat.random(in: 0.3...0.6),
            startTime: time,
            duration: Double.random(in: 0.8...1.5)
        )
        DispatchQueue.main.async {
            shootingStar = shooting
            nextShootingStarTime = time + Double.random(in: 8...15)
        }
    }
}
