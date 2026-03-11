import AVFoundation
import Combine

// MARK: - AmbientSound

enum AmbientSound: String, CaseIterable, Identifiable {
    case whiteNoise
    case brownNoise
    case pinkNoise
    case softRain
    case oceanWaves
    case cracklingFireplace
    case forestNight
    case heartbeat

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .whiteNoise: return "White Noise"
        case .brownNoise: return "Brown Noise"
        case .pinkNoise: return "Pink Noise"
        case .softRain: return "Soft Rain"
        case .oceanWaves: return "Ocean Waves"
        case .cracklingFireplace: return "Fireplace"
        case .forestNight: return "Forest Night"
        case .heartbeat: return "Heartbeat"
        }
    }

    var iconName: String {
        switch self {
        case .whiteNoise: return "waveform"
        case .brownNoise: return "water.waves"
        case .pinkNoise: return "wind"
        case .softRain: return "cloud.rain"
        case .oceanWaves: return "hurricane"
        case .cracklingFireplace: return "flame"
        case .forestNight: return "leaf"
        case .heartbeat: return "heart"
        }
    }
}

// MARK: - AmbientSoundManager

@MainActor
class AmbientSoundManager: ObservableObject {
    static let shared = AmbientSoundManager()

    @Published var isPlaying = false
    @Published var currentSound: AmbientSound = .whiteNoise
    @Published var volume: Float = 0.5

    private var audioEngine: AVAudioEngine?
    private var sourceNode: AVAudioSourceNode?
    private var fadeTimer: Timer?

    // State for noise generation algorithms
    private var brownNoiseLastSample: Float = 0
    private var pinkNoiseB: [Float] = [0, 0, 0, 0, 0, 0, 0]
    private var rainFilterState: Float = 0

    // Ocean waves state
    private var oceanPhase: Double = 0
    private var oceanFilterState: Float = 0

    // Crackling fireplace state
    private var fireBrownLast: Float = 0
    private var fireFilterState: Float = 0
    private var fireCrackles: [(remaining: Int, amplitude: Float, decay: Float, elapsed: Int)] = []

    // Forest night state
    private var forestChirpPhase: Double = 0
    private var forestWindPhase: Double = 0
    private var forestPinkB: [Float] = [0, 0, 0, 0, 0, 0, 0]

    // Heartbeat state
    private var heartPhase: Double = 0
    private var heartFilterState: Float = 0

    private init() {}

    // MARK: - Public API

    func play(_ sound: AmbientSound, fadeDuration: TimeInterval = 3.0) {
        stop(fadeDuration: 0)
        currentSound = sound
        resetNoiseState()
        configureAudioSession()

        let engine = AVAudioEngine()
        let mainMixer = engine.mainMixerNode
        let outputFormat = mainMixer.outputFormat(forBus: 0)
        let sampleRate = outputFormat.sampleRate
        let channelCount = outputFormat.channelCount

        let generator = createGenerator(for: sound)

        let source = AVAudioSourceNode(format: outputFormat) { _, _, frameCount, audioBufferList -> OSStatus in
            let bufferList = UnsafeMutableAudioBufferListPointer(audioBufferList)
            for frame in 0..<Int(frameCount) {
                let sample = generator(sampleRate)
                for channel in 0..<Int(channelCount) {
                    let buffer = bufferList[channel]
                    let ptr = buffer.mData?.assumingMemoryBound(to: Float.self)
                    ptr?[frame] = sample
                }
            }
            return noErr
        }

        engine.attach(source)
        engine.connect(source, to: mainMixer, format: outputFormat)

        mainMixer.outputVolume = 0

        do {
            try engine.start()
            audioEngine = engine
            sourceNode = source
            isPlaying = true
            fadeVolume(to: volume, duration: fadeDuration)
        } catch {
            // Audio engine failed to start
        }
    }

    func stop(fadeDuration: TimeInterval = 1.0) {
        guard isPlaying else { return }
        fadeVolume(to: 0, duration: fadeDuration) { [weak self] in
            self?.audioEngine?.stop()
            if let source = self?.sourceNode {
                self?.audioEngine?.detach(source)
            }
            self?.audioEngine = nil
            self?.sourceNode = nil
            self?.isPlaying = false
        }
    }

    func setVolume(_ newVolume: Float) {
        volume = newVolume
        audioEngine?.mainMixerNode.outputVolume = newVolume
    }

    // MARK: - Audio Session

    private func configureAudioSession() {
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playback, mode: .default, options: [.mixWithOthers])
            try session.setActive(true)
        } catch {
            // Audio session configuration failed
        }
    }

    // MARK: - Noise Generators

    private func resetNoiseState() {
        brownNoiseLastSample = 0
        pinkNoiseB = [0, 0, 0, 0, 0, 0, 0]
        rainFilterState = 0
        oceanPhase = 0
        oceanFilterState = 0
        fireBrownLast = 0
        fireFilterState = 0
        fireCrackles = []
        forestChirpPhase = 0
        forestWindPhase = 0
        forestPinkB = [0, 0, 0, 0, 0, 0, 0]
        heartPhase = 0
        heartFilterState = 0
    }

    private func createGenerator(for sound: AmbientSound) -> (Double) -> Float {
        switch sound {
        case .whiteNoise:
            return { [weak self] _ in
                guard self != nil else { return 0 }
                return Float.random(in: -1...1) * 0.3
            }

        case .brownNoise:
            return { [weak self] _ in
                guard let self = self else { return 0 }
                let white = Float.random(in: -1...1)
                self.brownNoiseLastSample = (self.brownNoiseLastSample + (0.02 * white)) / 1.02
                return self.brownNoiseLastSample * 3.5 * 0.3
            }

        case .pinkNoise:
            return { [weak self] _ in
                guard let self = self else { return 0 }
                let white = Float.random(in: -1...1)
                // Voss-McCartney pink noise approximation
                self.pinkNoiseB[0] = 0.99886 * self.pinkNoiseB[0] + white * 0.0555179
                self.pinkNoiseB[1] = 0.99332 * self.pinkNoiseB[1] + white * 0.0750759
                self.pinkNoiseB[2] = 0.96900 * self.pinkNoiseB[2] + white * 0.1538520
                self.pinkNoiseB[3] = 0.86650 * self.pinkNoiseB[3] + white * 0.3104856
                self.pinkNoiseB[4] = 0.55000 * self.pinkNoiseB[4] + white * 0.5329522
                self.pinkNoiseB[5] = -0.7616 * self.pinkNoiseB[5] - white * 0.0168980
                let pink = self.pinkNoiseB[0] + self.pinkNoiseB[1] + self.pinkNoiseB[2]
                    + self.pinkNoiseB[3] + self.pinkNoiseB[4] + self.pinkNoiseB[5]
                    + self.pinkNoiseB[6] + white * 0.5362
                self.pinkNoiseB[6] = white * 0.115926
                return pink * 0.11 * 0.3
            }

        case .softRain:
            return { [weak self] sampleRate in
                guard let self = self else { return 0 }
                let white = Float.random(in: -1...1)
                // Low-pass filter for a softer, rain-like sound
                let cutoff: Float = 800.0 / Float(sampleRate)
                let rc = 1.0 / (cutoff * 2.0 * .pi)
                let dt: Float = 1.0 / Float(sampleRate)
                let alpha = dt / (rc + dt)
                self.rainFilterState = self.rainFilterState + alpha * (white - self.rainFilterState)
                // Add occasional louder "drops"
                let drop: Float = Float.random(in: 0...1) < 0.001 ? Float.random(in: 0.3...0.6) : 0
                return (self.rainFilterState * 1.5 + drop) * 0.3
            }

        case .oceanWaves:
            return { [weak self] sampleRate in
                guard let self = self else { return 0 }
                let dt = 1.0 / sampleRate
                self.oceanPhase += dt

                // Wave swell with varying period (8-12s cycle)
                let cyclePeriod = 10.0 + 2.0 * sin(self.oceanPhase / 60.0)
                let swell = Float(0.5 * (1.0 - cos(2.0 * .pi * self.oceanPhase / cyclePeriod)))

                // Low-pass filtered noise for wave wash
                let white = Float.random(in: -1...1)
                let cutoff: Float = 400.0 / Float(sampleRate)
                let rc = 1.0 / (cutoff * 2.0 * .pi)
                let dtf: Float = 1.0 / Float(sampleRate)
                let alpha = dtf / (rc + dtf)
                self.oceanFilterState = self.oceanFilterState + alpha * (white - self.oceanFilterState)

                // Foam/hiss at wave peaks
                let foam = white * 0.15 * swell * swell

                return (self.oceanFilterState * swell * 0.8 + foam) * 0.3
            }

        case .cracklingFireplace:
            return { [weak self] sampleRate in
                guard let self = self else { return 0 }

                // Warm base: brown noise filtered at ~200 Hz
                let white = Float.random(in: -1...1)
                self.fireBrownLast = (self.fireBrownLast + (0.02 * white)) / 1.02
                let cutoff: Float = 200.0 / Float(sampleRate)
                let rc = 1.0 / (cutoff * 2.0 * .pi)
                let dtf: Float = 1.0 / Float(sampleRate)
                let alpha = dtf / (rc + dtf)
                self.fireFilterState = self.fireFilterState + alpha * (self.fireBrownLast * 3.5 - self.fireFilterState)
                let warmBase = self.fireFilterState

                // Spawn new crackle impulses (~13 per second)
                if Float.random(in: 0...1) < 0.0003 && self.fireCrackles.count < 4 {
                    let amp = Float.random(in: 0.4...1.0)
                    let decay = Float.random(in: 100...400)
                    self.fireCrackles.append((remaining: Int(decay * 3), amplitude: amp, decay: decay, elapsed: 0))
                }

                // Sum active crackles
                var crackleSum: Float = 0
                self.fireCrackles = self.fireCrackles.compactMap { crackle in
                    var c = crackle
                    let env = c.amplitude * exp(-Float(c.elapsed) / c.decay)
                    crackleSum += env * Float.random(in: -1...1)
                    c.elapsed += 1
                    c.remaining -= 1
                    return c.remaining > 0 ? c : nil
                }

                return (warmBase * 0.6 + crackleSum * 0.4) * 0.3
            }

        case .forestNight:
            return { [weak self] sampleRate in
                guard let self = self else { return 0 }
                let dt = 1.0 / sampleRate
                self.forestChirpPhase += dt
                self.forestWindPhase += dt

                // Cricket 1: 4500 Hz chirps every ~0.5s
                let chirpCycle1 = self.forestChirpPhase.truncatingRemainder(dividingBy: 0.5)
                let cricket1: Float
                if chirpCycle1 < 0.15 {
                    let env = Float(1.0 - chirpCycle1 / 0.15)
                    cricket1 = Float(sin(2.0 * .pi * 4500.0 * self.forestChirpPhase)) * env * 0.3
                } else {
                    cricket1 = 0
                }

                // Cricket 2: 5200 Hz, offset by 0.25s
                let chirpCycle2 = (self.forestChirpPhase + 0.25).truncatingRemainder(dividingBy: 0.5)
                let cricket2: Float
                if chirpCycle2 < 0.12 {
                    let env = Float(1.0 - chirpCycle2 / 0.12)
                    cricket2 = Float(sin(2.0 * .pi * 5200.0 * self.forestChirpPhase)) * env * 0.25
                } else {
                    cricket2 = 0
                }

                // Wind: pink noise with slow amplitude modulation
                let white = Float.random(in: -1...1)
                self.forestPinkB[0] = 0.99886 * self.forestPinkB[0] + white * 0.0555179
                self.forestPinkB[1] = 0.99332 * self.forestPinkB[1] + white * 0.0750759
                self.forestPinkB[2] = 0.96900 * self.forestPinkB[2] + white * 0.1538520
                self.forestPinkB[3] = 0.86650 * self.forestPinkB[3] + white * 0.3104856
                self.forestPinkB[4] = 0.55000 * self.forestPinkB[4] + white * 0.5329522
                self.forestPinkB[5] = -0.7616 * self.forestPinkB[5] - white * 0.0168980
                let pink = self.forestPinkB[0] + self.forestPinkB[1] + self.forestPinkB[2]
                    + self.forestPinkB[3] + self.forestPinkB[4] + self.forestPinkB[5]
                    + self.forestPinkB[6] + white * 0.5362
                self.forestPinkB[6] = white * 0.115926

                let windMod = Float(0.3 + 0.2 * sin(2.0 * .pi * self.forestWindPhase / 6.0))
                let wind = pink * 0.11 * windMod

                return (wind * 0.5 + cricket1 * 0.25 + cricket2 * 0.25) * 0.3
            }

        case .heartbeat:
            return { [weak self] sampleRate in
                guard let self = self else { return 0 }
                let dt = 1.0 / sampleRate
                self.heartPhase += dt

                // 60 BPM = 1 beat per second
                let t = self.heartPhase.truncatingRemainder(dividingBy: 1.0)

                // Lub (first heart sound) at t=0, 60 Hz
                let lubEnv = Float(exp(-(t * t) / (2.0 * 0.02 * 0.02)))
                let lub = Float(sin(2.0 * .pi * 60.0 * t)) * lubEnv * 0.8

                // Dub (second heart sound) at t=0.3, 80 Hz
                let tDub = t - 0.3
                let dubEnv = Float(exp(-(tDub * tDub) / (2.0 * 0.015 * 0.015)))
                let dub = Float(sin(2.0 * .pi * 80.0 * t)) * dubEnv * 0.6

                let beat = lub + dub

                // Gentle low-pass filter to soften
                let cutoff: Float = 150.0 / Float(sampleRate)
                let rc = 1.0 / (cutoff * 2.0 * .pi)
                let dtf: Float = 1.0 / Float(sampleRate)
                let alphaF = dtf / (rc + dtf)
                self.heartFilterState = self.heartFilterState + alphaF * (beat - self.heartFilterState)

                return self.heartFilterState * 0.3
            }
        }
    }

    // MARK: - Volume Fade

    private func fadeVolume(
        to target: Float,
        duration: TimeInterval,
        completion: (() -> Void)? = nil
    ) {
        fadeTimer?.invalidate()

        guard duration > 0 else {
            audioEngine?.mainMixerNode.outputVolume = target
            completion?()
            return
        }

        let steps = 30
        let interval = duration / Double(steps)
        let startVolume = audioEngine?.mainMixerNode.outputVolume ?? 0
        let delta = (target - startVolume) / Float(steps)
        var step = 0

        fadeTimer = Timer.scheduledTimer(withTimeInterval: interval, repeats: true) { [weak self] timer in
            step += 1
            let newVol = startVolume + delta * Float(step)
            self?.audioEngine?.mainMixerNode.outputVolume = newVol
            if step >= steps {
                timer.invalidate()
                self?.audioEngine?.mainMixerNode.outputVolume = target
                completion?()
            }
        }
    }
}
