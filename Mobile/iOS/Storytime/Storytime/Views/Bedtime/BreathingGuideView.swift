import SwiftUI

struct BreathingGuideView: View {
    @EnvironmentObject var localeManager: LocaleManager
    // 4-7-8 breathing pattern: 4s inhale, 7s hold, 8s exhale = 19s total
    private let inhaleDuration: Double = 4.0
    private let holdDuration: Double = 7.0
    private let exhaleDuration: Double = 8.0

    private var totalCycle: Double { inhaleDuration + holdDuration + exhaleDuration }

    private let minScale: CGFloat = 0.3
    private let maxScale: CGFloat = 1.0
    private let circleSize: CGFloat = 150

    var body: some View {
        TimelineView(.animation) { timeline in
            let time = timeline.date.timeIntervalSinceReferenceDate
            let cycleTime = time.truncatingRemainder(dividingBy: totalCycle)

            let (scale, opacity, label) = computeState(cycleTime: cycleTime)

            VStack(spacing: 24) {
                Spacer()

                ZStack {
                    // Outer glow ring
                    Circle()
                        .stroke(Color.white.opacity(Double(opacity) * 0.1), lineWidth: 8)
                        .frame(width: circleSize * maxScale + 20, height: circleSize * maxScale + 20)

                    // Main breathing circle
                    Circle()
                        .stroke(Color.white.opacity(Double(opacity) * 0.3), lineWidth: 3)
                        .frame(width: circleSize, height: circleSize)
                        .scaleEffect(scale)

                    // Inner fill
                    Circle()
                        .fill(Color.white.opacity(Double(opacity) * 0.05))
                        .frame(width: circleSize, height: circleSize)
                        .scaleEffect(scale)
                }

                // Phase label
                Text(label)
                    .font(.system(size: 16, weight: .light))
                    .foregroundStyle(Color.white.opacity(0.4))
                    .animation(.easeInOut(duration: 0.5), value: label)

                Spacer()
                Spacer()
            }
        }
        .allowsHitTesting(false)
    }

    private func computeState(cycleTime: Double) -> (CGFloat, CGFloat, String) {
        if cycleTime < inhaleDuration {
            // Inhale: scale from min to max
            let progress = cycleTime / inhaleDuration
            let eased = easeInOut(progress)
            let scale = minScale + (maxScale - minScale) * CGFloat(eased)
            return (scale, 0.8 + 0.2 * CGFloat(eased), localeManager.localized("breathing_in"))
        } else if cycleTime < inhaleDuration + holdDuration {
            // Hold: stay at max with gentle pulse
            let holdProgress = (cycleTime - inhaleDuration) / holdDuration
            let pulse: CGFloat = 0.8 + 0.2 * CGFloat(sin(holdProgress * .pi * 2))
            return (maxScale, pulse, localeManager.localized("breathing_hold"))
        } else {
            // Exhale: scale from max to min
            let exhaleProgress = (cycleTime - inhaleDuration - holdDuration) / exhaleDuration
            let eased = easeInOut(exhaleProgress)
            let scale = maxScale - (maxScale - minScale) * CGFloat(eased)
            return (scale, 0.8 - 0.2 * CGFloat(eased), localeManager.localized("breathing_out"))
        }
    }

    private func easeInOut(_ t: Double) -> Double {
        // Smooth ease-in-out curve
        return t < 0.5 ? 2 * t * t : 1 - pow(-2 * t + 2, 2) / 2
    }
}
